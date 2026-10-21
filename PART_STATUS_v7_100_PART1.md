# v7.100 Part 1 - auto test / self-wake / verify / copy
FIXED: automatic self test is always quiet now (no torch, volume, mic grab, Settings jump, BACK, popup). Hardware checks run only on POORI JAANCH ABHI.
FIXED: AUTO VERIFY waits up to 24 s, asks the page again, page answers by itself after 3.5 s, no false FAIL while screen is closed.
FIXED: Settings TEST REPORT, COPY REPORT, SHARE visible again (was hidden by v76_pro_css).
FIXED: Porcupine wake now needs real loudness + escalating cooldown after false wakes.
NOT YET COMPILED: compile + JUnit + UI wiring run is done once in Part 5.
