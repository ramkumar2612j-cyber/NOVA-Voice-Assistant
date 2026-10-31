#!/usr/bin/env python3
"""v7.152 contract: light popup is fully wired (Settings switch + light chat box + honest question answers)
and the GLM 5.3 model id lives in ONE place. Add-only: nothing from v7.149-151 may be missing."""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, '..')
J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
def rd(f): return open(os.path.join(J, f), encoding='utf8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)

ui = rd('ui.html') if os.path.exists(os.path.join(J, 'ui.html')) else open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf8').read()
ht = rd('HudTheme.kt'); nh = rd('NovaHud.kt'); ns = rd('NovaService.kt'); ma = rd('MainActivity.kt')
fv = rd('FeatureVoice.kt'); fs = rd('FeatureStatus.kt'); fvis = rd('FreeVision.kt'); fp = rd('FreeProviders.kt')
hg = rd('HelpGuide.kt')

# 1. Settings switch (opt-in, default OFF): ui checkbox <-> getSettings/saveSettings <-> ExtCfg
need('id="s_light"' in ui, 'ui.html: s_light checkbox missing')
need("lightPopup:$('s_light').checked" in ui, 'ui.html: collect() must send lightPopup')
need("$('s_light').checked=!!S.lightPopup" in ui, 'ui.html: fillPanel must read S.lightPopup')
need('"default OFF" in ui', 'ui.html: the light popup row must say default OFF')
need('.put("lightPopup", ExtCfg.lightPopup(this@MainActivity))' in ma, 'MainActivity.getSettings: lightPopup missing')
need('ExtCfg.setLightPopup(this@MainActivity, o.optBoolean("lightPopup", false))' in ma, 'MainActivity.saveSettings: lightPopup missing (default false = opt-in kept)')
need('fun setLightPopup' in rd('ExtCfg.kt'), 'ExtCfg.setLightPopup missing')

# 2. Question guard: a question must NOT flip the theme, old orders must still work
need('fun isQuestion(text: String): Boolean' in ht, 'HudTheme.isQuestion missing')
need('!HudTheme.isQuestion(text)' in ns, 'NovaService must skip the theme switch for questions')
need(ns.count('HudTheme.parse(text)') >= 1 and 'fun parse(text' in ht, 'HudTheme.parse must stay (old orders keep working)')
need('popuptheme' in fv, 'FeatureVoice.popuptheme status key missing')
need('State(ExtCfg.lightPopup(ctx))' in fv, 'FeatureVoice must READ the theme, never set it')
code = re.sub(r'/\*.*?\*/', '', fv, flags=re.S)
for w in ['setLightPopup', 'putBoolean', 'edit()']:
    need(w not in code, 'FeatureVoice must stay read-only: found ' + w)
need('"popuptheme"' in fs, 'FeatureStatus.popuptheme row missing')
for t in ['popupThemeOrdersStillSwitch', 'popupThemeQuestionsAreNeverOrders']:
    need(t in open(os.path.join(R, 'app/src/test/java/com/nova/assistant/HudThemeTest.kt'), encoding='utf8').read(), 'HudThemeTest missing ' + t)
ft = open(os.path.join(R, 'app/src/test/java/com/nova/assistant/FeatureVoiceTest.kt'), encoding='utf8').read()
for t in ['popupThemeQuestionIsAStatusQuestion', 'popupThemeReplyNamesTheStateAndTheNextTap']:
    need(t in ft, 'FeatureVoiceTest missing ' + t)

# 3. Light chat box: the dark Ultron colours must stay as the else-branch, light uses HudTheme
need('if (lightBox) HudTheme.fillColors(true, false)' in nh, 'NovaHud.openChat: light gradient missing')
need('0xF2081A48.toInt()' in nh, 'NovaHud.openChat: dark Ultron glass must stay for the dark popup')
need('val lightBox = try { ExtCfg.lightPopup(ctx) } catch (e: Throwable) { false }' in nh, 'NovaHud.openChat: theme read missing (default dark)')
need('HudTheme.flat(true)' in nh, 'NovaHud.openChat: light flat colour missing')

# 4. GLM 5.3 id in ONE place: FreeVision.GLM derives from FreeProviders.OPENROUTER_GLM
need('FreeProviders.OPENROUTER_GLM.models' in fvis, 'FreeVision.GLM must reuse the ONE model list')
need('FreeProviders.OPENROUTER_GLM.url' in fvis, 'FreeVision.GLM must reuse the ONE url')
need(fp.count('listOf("z-ai/glm-5.3-flash:free"), "openrouter", "", true, 4000') == 1, 'the GLM 5.3 model id must appear EXACTLY once as a real model list in FreeProviders.kt')
need(fvis.count('z-ai/glm-5.3-flash:free') == 0, 'the GLM 5.3 model id must NOT be duplicated in FreeVision.kt')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/FreeProvidersTest.kt')) and
     'glmVisionUsesTheSameSingleSourceOfTruthAsTheBrain' in open(os.path.join(R, 'app/src/test/java/com/nova/assistant/FreeProvidersTest.kt'), encoding='utf8').read(),
     'FreeProvidersTest single-source test missing')

# 5. Self-knowledge: the brain topic is in HelpGuide AND the MADAD list (test_help_contract keeps them equal, this pins the id)
need('Topic("aibrain"' in hg, 'HelpGuide aibrain topic missing')
need('"id": "aibrain"' in ui, 'ui.html CAPS aibrain missing')
need('GLM 5.3 Flash brain' in ui and 'GLM 5.3 Flash (brain)' in rd('ApiUsage.kt'), 'the brain must be named in MADAD and LIMITS the same way')

if bad:
    print('FAIL v7152 contract'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS v7152 light popup + single-source contract')
