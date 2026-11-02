# AUTOPSY R2 (open website / download / teach / photo)
Traced: openWeb/openChromeTab/openAppOrSite/openUrl (all through launch(), verified), downloadApp (honest codes), TeachStore apply/add, imageBegin -> FreeImage -> ImageGen.saveToGallery.
Fixed:
1. Photo failure hid the real reason (key refused / limit / no network); now spoken and shown.
2. Gallery save failure left a hidden half-written IS_PENDING row; now deleted.
Checked, no bug: Chrome tab opening, Play Store install honesty, teach alias apply guards.
Kotlin compile NOT RUN, phone NOT TESTED. Test: tools/test_autopsy_part5_contract.py
