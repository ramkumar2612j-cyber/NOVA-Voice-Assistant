# v7.97 - notification reader / accessibility dead ends
FIX: listener re-bind (requestRebind + 2 s wait) and settings pages opened automatically with exact steps. See CHANGELOG v7.97.
RUN: Kotlin compile 0 errors, JUnit 1454/1454, python guards 146/146. NOT RUN: GitHub Actions. NOT TESTED on phone.
Owner: after installing the APK, switch ON Accessibility and Notification access for NOVA once (Android does not allow an app to do it). If "insta pe naya message" still says not connected: OFF -> ON the Notification access switch.
