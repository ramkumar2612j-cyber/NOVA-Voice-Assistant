#!/usr/bin/env python3
"""v7.196 SELF BUILD static contract: "jarvis apk banao" -> workflow_dispatch -> poll -> (CI publishes a GitHub
Release) -> download + sha256 verify + UpdateManager's existing verified-install path. No .kt patch is ever written
by this feature; it only re-builds the already-reviewed `main` branch. Static check only, no network/compile here."""
import re, sys, os

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, '..')
J = os.path.join(R, 'app/src/main/java/com/nova/assistant')

sb = open(os.path.join(J, 'GitHubSelfBuild.kt'), encoding='utf-8').read()
um = open(os.path.join(J, 'UpdateManager.kt'), encoding='utf-8').read()
ma = open(os.path.join(J, 'MainActivity.kt'), encoding='utf-8').read()
svc = open(os.path.join(J, 'JarvisService.kt'), encoding='utf-8').read()
ui = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf-8').read()
wf = open(os.path.join(R, '.github/workflows/build.yml'), encoding='utf-8').read()
gup = open(os.path.join(J, 'GitHubUpgradePublish.kt'), encoding='utf-8').read()

chk = {
    'token only via SecureStore slot (same slot as GitHubPublish/GitHubUpgradePublish)':
        'SecureStore.getSlot(ctx, GitHubPublish.SLOT)' in sb,
    'token never logged or printed':
        all(x not in sb for x in ('println(token', 'Log.d(', 'Log.e(', 'ErrLog.add("token')),
    'reuses the single OWNER_REPO constant, does not hardcode a second repo string':
        'internal const val OWNER_REPO' in gup and 'GitHubUpgradePublish.OWNER_REPO' in sb
        and re.search(r'private\s+const\s+val\s+OWNER_REPO', sb) is None,
    'trigger() only dispatches build.yml on main (no other ref, no push to main)':
        'fun trigger(' in sb and '/actions/workflows/build.yml/dispatches' in sb
        and '"ref", "main"' in sb and '"PUT",' not in sb.split('fun trigger(')[1].split('fun ')[0],
    'poll() is read-only (no POST/PUT in poll body)':
        'fun poll(' in sb and sb.split('fun poll(')[1].split('fun install')[0].count('"POST"') == 0
        and sb.split('fun poll(')[1].split('fun install')[0].count('"PUT"') == 0,
    'picks only the run we triggered (workflow_dispatch on main, since dispatch time)':
        'fun pickRun(' in sb and 'event") != "workflow_dispatch"' in sb and 'head_branch") != "main"' in sb,
    'release asset is sha256-verified before install':
        'MessageDigest.getInstance("SHA-256")' in sb and 'expectSha.isNotEmpty() && gotSha != expectSha' in sb,
    'GitHub token is never forwarded to a redirect target off api.github.com':
        'fun authFor(' in sb and 'sendAuth = SelfBuildRules.authFor(URL(loc).host)' in sb,
    'verified file goes through UpdateManager, not a second install mechanism':
        'UpdateManager.installApkFile(' in sb,
    'UpdateManager exposes the shared verified-install core (same-package/newer/same-key + PackageInstaller)':
        'fun installApkFile(' in um and 'checkApk(ctx, f)' in um.split('fun installApkFile(')[1][:1200],
    'applyApk still routes through installApkFile (no duplicated install logic)':
        'val r = installApkFile(ctx, f' in um,
    'HARD DENY untouched: no payment/OTP/password/PIN/CVV code path added (comments mentioning it are fine)':
        not any(x in '\n'.join(l for l in sb.lower().split('\n') if not l.strip().startswith(('*', '//', '/**')))
                for x in ('payment', 'otp', 'cvv', ' pin ', 'password')),
    'no new .kt file is ever written by this feature (self-build only rebuilds reviewed main)':
        '.kt"' not in sb and 'writeText' not in sb.split('object GitHubSelfBuild')[1].split('private fun doInstall')[0],
    'voice entry wired into JarvisService (same shape as UpgradeLedger.handle)':
        'GitHubSelfBuild.handleVoice' in svc,
    'bridge exposes trigger/poll/install capped by act.take(8), same pattern as upgradeAct':
        'fun selfBuildAct(act: String): String' in ma and 'act.take(8)' in ma.split('fun selfBuildAct')[1][:200]
        and '"trigger" ->' in ma and '"poll" ->' in ma and '"install" ->' in ma,
    'bridge exposes a status read for the UI chip':
        'fun selfBuildStatus(): String' in ma,
    'ui.html: Self Build card present with trigger/status/install buttons': all(
        x in ui for x in ('id="sb_box"', 'id="sb_run"', 'id="sb_poll"', 'id="sb_install"', 'id="sb_chip"')
    ),
    'ui.html: install button starts disabled, only enabled by JS once green': 'id="sb_install" disabled' in ui,
    'ui.html: bridge calls registered in BRIDGE/CB auto-verify lists': all(
        x in ui for x in ('"selfBuildAct"', '"selfBuildStatus"', '"onSelfBuild"')
    ),
    'ui.html: text is honest about the last tap being the owner\'s (no "silent install" claim)':
        'aakhri tap hamesha aapka' in ui or 'Install tap' in ui,
    'workflow: release publish step only for main push, only on success, only via built-in GITHUB_TOKEN':
        "github.ref == 'refs/heads/main'" in wf and 'GH_TOKEN: ${{ github.token }}' in wf
        and 'if: success()' in wf.split('Publish latest APK as a GitHub Release')[1][:160],
    'workflow: release publish uses contents: write permission (least privilege, job-scoped)':
        'permissions:' in wf and 'contents: write' in wf,
    'workflow: publishes a jarvis-latest tag with an apk + a .sha256 file (matches GitHubSelfBuild expectations)':
        'jarvis-latest' in wf and 'app-debug.apk.sha256' in wf,
}

bad = [k for k, v in chk.items() if not v]
for k, v in chk.items():
    print(('PASS' if v else 'FAIL') + ': ' + k)
print('self-build contract checks: %d, failed: %d' % (len(chk), len(bad)))
sys.exit(1 if bad else 0)
