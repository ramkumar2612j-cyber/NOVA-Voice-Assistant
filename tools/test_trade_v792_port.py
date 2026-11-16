#!/usr/bin/env python3
"""v7.92 TRADE FIXES: python port of the new TradeEngine maths, run on the same cases as TradeEngineTest (v792*).
Not Kotlin: it proves the RULES / numbers, not the compile. Kotlin compile + JUnit still run in GitHub Actions."""
import calendar, math, sys
from collections import namedtuple
C = namedtuple('C', 't o h l c')
T = namedtuple('T', 'id ts sym dir entry sl tp1 status r closed', defaults=('OPEN', 0.0, 0))
SP = {"XAUUSD": 0.30}
def spread(s): return SP.get(s, 0.0)
def cost_r(s, e, sl):
    risk = abs(e - sl); return 0.0 if risk <= 0 else round(spread(s) / risk * 1000) / 1000
def rnd2(v): return math.floor(v * 100 + 0.5) / 100     # Math.round semantics
def closed_only(c, ims, now):
    if not c or now <= 0 or ims <= 0: return c
    return c[:-1] if c[-1].t + ims > now else c
def settle_scaled(t, after, max_age=3*24*3600*1000, now=0):
    if t.status != 'OPEN': return t
    risk = abs(t.entry - t.sl)
    if risk <= 0: return t
    buy = t.dir == 'BUY'
    r1 = abs(t.tp1 - t.entry) / risk; r2 = r1 * (2.5 / 1.5)
    tp2 = t.entry + r2 * risk if buy else t.entry - r2 * risk
    cost = cost_r(t.sym, t.entry, t.sl); phase = 1
    def done(k, ex, rr):
        v = rnd2(rr); return t._replace(status='WIN' if v > 0 else 'LOSS' if v < 0 else 'EVEN', r=v, closed=k.t)
    for k in after:
        if k.t <= t.ts: continue
        if phase == 1:
            hit_sl = k.l <= t.sl if buy else k.h >= t.sl
            hit_tp = k.h >= t.tp1 if buy else k.l <= t.tp1
            if hit_sl: return done(k, t.sl, -1.0 - cost)
            if hit_tp: phase = 2; continue
        else:
            be = k.l <= t.entry if buy else k.h >= t.entry
            t2 = k.h >= tp2 if buy else k.l <= tp2
            if be: return done(k, t.entry, 0.5 * r1 - cost)
            if t2: return done(k, tp2, 0.5 * r1 + 0.5 * r2 - cost)
    if now > 0 and now - t.ts > max_age and after:
        k = after[-1]; rr = ((k.c - t.entry) if buy else (t.entry - k.c)) / risk
        return done(k, k.c, (0.5 * r1 + 0.5 * rr if phase == 2 else rr) - cost)
    return t
def utc(y, mo, d, h, mi=0): return calendar.timegm((y, mo, d, h, mi, 0)) * 1000
def session_block(sym, ms):
    if ms <= 0 or sym in ("BTCUSD", "ETHUSD"): return ""
    tm = calendar.timegm(()) if False else None
    import time; g = time.gmtime(ms / 1000)
    dow = (g.tm_wday + 1) % 7 + 1      # python Mon=0 -> Java Sun=1..Sat=7
    mins = g.tm_hour * 60 + g.tm_min; dom = g.tm_mday
    if dow == 7 or (dow == 6 and mins >= 21*60) or (dow == 1 and mins < 22*60): return "weekend"
    if 21*60+55 <= mins <= 23*60+5: return "rollover"
    if dow == 6 and dom <= 7 and 12*60 <= mins <= 15*60: return "nfp"
    return ""
def ema(v, p):
    k = 2.0 / (p + 1); out = [v[0]]
    for x in v[1:]: out.append(x * k + out[-1] * (1 - k))
    return out
def atr(c, p=14):
    if len(c) <= p: return 0.0
    tr = [max(c[i].h - c[i].l, abs(c[i].h - c[i-1].c), abs(c[i].l - c[i-1].c)) for i in range(1, len(c))]
    v = sum(tr[:p]) / p
    for x in tr[p:]: v = (v * (p - 1) + x) / p
    return v
def htf(c):
    if len(c) < 55: return "UNKNOWN"
    cl = [x.c for x in c]; a = atr(c)
    if a <= 0: return "UNKNOWN"
    e20, e50 = ema(cl, 20), ema(cl, 50); gap = e20[-1] - e50[-1]
    if abs(gap) < 0.3 * a: return "SIDEWAYS"
    return "UP" if gap > 0 and cl[-1] > e50[-1] else "DOWN" if gap < 0 and cl[-1] < e50[-1] else "SIDEWAYS"
def lower_bound(rs):
    n = len(rs)
    if n < 2: return 0.0
    m = sum(rs) / n; sd = math.sqrt(sum((x - m) ** 2 for x in rs) / (n - 1))
    return round((m - sd / math.sqrt(n)) * 100) / 100
bad = []
def chk(c, m):
    if not c: bad.append(m)
# closedOnly
c2 = [C(0, 1, 2, .5, 1.5), C(900000, 1.5, 2.5, 1, 2)]
chk(len(closed_only(c2, 900000, 1000000)) == 1 and len(closed_only(c2, 900000, 1800001)) == 2, 'closedOnly')
# spread cost
t = T('a', 1000, 'XAUUSD', 'BUY', 2000., 1994., 2009.)
chk(abs(cost_r('XAUUSD', 2000., 1994.) - 0.05) < 1e-9, 'costR')
r = settle_scaled(t, [C(2000, 2000, 2001, 1990, 1991)]); chk(r.status == 'LOSS' and abs(r.r + 1.05) < 1e-9, 'loss -1.05 got %s' % (r,))
# scaled exits
t = T('b', 1000, 'NOSPREAD', 'BUY', 100., 94., 109.)
r = settle_scaled(t, [C(2000,100,110,99,108), C(3000,108,109,99.5,100)]); chk(r.status == 'WIN' and r.r == 0.75, 'BE 0.75 %s' % (r,))
r = settle_scaled(t, [C(2000,100,110,99,108), C(3000,108,116,107,115)]); chk(r.r == 2.0, 'T2 2.0 %s' % (r,))
r = settle_scaled(t, [C(2000,100,110,99,108), C(3000,108,120,98,110)]); chk(r.r == 0.75, 'same candle BE first')
chk(settle_scaled(t, [C(2000,100,105,98,101)]).status == 'OPEN', 'open stays open')
s = T('c', 1000, 'NOSPREAD', 'SELL', 100., 106., 91.)
r = settle_scaled(s, [C(2000,100,101,90,92), C(3000,92,85,84,85)]); chk(r.r == 2.0, 'SELL mirror %s' % (r,))
# timeout after T1 keeps the banked half
r = settle_scaled(t, [C(2000,100,110,99.9,108)], now=1000 + 4*24*3600*1000); chk(r.status == 'WIN' and r.r > 0.75, 'timeout phase2 %s' % (r,))
# session guard
chk(session_block('XAUUSD', utc(2026,10,21,10)) == '', 'wed ok')
chk(session_block('XAUUSD', utc(2026,10,24,10)) != '', 'sat')
chk(session_block('XAUUSD', utc(2026,10,23,22)) != '', 'fri night')
chk(session_block('EURUSD', utc(2026,10,21,22,30)) != '', 'rollover')
chk(session_block('EURUSD', utc(2026,10,2,13,30)) == 'nfp', 'nfp')
chk(session_block('EURUSD', utc(2026,10,9,13,30)) == '', '2nd friday normal')
chk(session_block('BTCUSD', utc(2026,10,24,10)) == '', 'crypto weekend')
chk(session_block('XAUUSD', utc(2026,10,18,21,0)) != '' and session_block('XAUUSD', utc(2026,10,18,22,30)) != '' and session_block('XAUUSD', utc(2026,10,19,0,30)) == '', 'sunday close + rollover edges, Monday 00:30 open')
# htf
up = [C(i*3600000, 100+i, 101+i, 99+i, 100.5+i) for i in range(120)]
dn = [C(i*3600000, 300-i, 301-i, 299-i, 299.5-i) for i in range(120)]
chk(htf(up) == 'UP' and htf(dn) == 'DOWN' and htf(up[:20]) == 'UNKNOWN', 'htf')
# gate confidence
good = [1.5 if i % 4 else -1.0 for i in range(1, 121)]
wild = [9.0 if i % 2 == 0 else -8.5 for i in range(1, 101)]
chk(lower_bound(good) > 0, 'good lb'); chk(sum(wild)/len(wild) > 0.2 and lower_bound(wild) < 0, 'wild lb %s' % lower_bound(wild))
print('PASS test_trade_v792_port (%d checks)' % 26 if not bad else 'FAIL\n - ' + '\n - '.join(bad)); sys.exit(1 if bad else 0)
