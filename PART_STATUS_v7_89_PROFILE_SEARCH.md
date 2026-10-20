# v7.89 profile search - status
RUN: python guards 144/144 PASS; audit_dead_ui REAL GAPS 0; parser logic checked on 26 phrases (python port).
NOT RUN: Kotlin compile, JUnit (no network in sandbox) -> first compile = GitHub Actions "Build and Test APK".
NOT TESTED on phone: "nova instagram par rahul_sharma search karo", "nova facebook par rohit verma dhundo", "instagram kholo" must NOT trigger it.
Refused on purpose: face / photo lookup, number, address, location, password.
If Actions fails: send the red error lines; fix only ProfileSearchRules.kt / NovaService.runProfileSearch / ProfileSearchRulesTest.kt.
