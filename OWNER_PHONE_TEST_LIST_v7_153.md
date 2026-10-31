# NOVA v7.153 — phone test list (WiFi Kit; ek-ek karke har number ke aage PASS / FAIL likho)
Pehle GitHub build GREEN hona chahiye. Fail par ek line + screenshot bhejo. Purani list (v7.152) bhi chalti rahegi.

## TAIYARI
1. App kholo: pehli baar Location permission ka dialog aaya? ALLOW karo. (Setup round 3)
2. Settings me WIFI GUARD naam ka checkbox dikhta hai? Default OFF hai?

## SCAN (sabse pehla tool)
3. Bolo "nova wifi scan karo": networks ki list aayi? Har line me signal X/4, band (2.4/5 GHz), WPA2/WPA3/FREE likha hai?
4. WiFi band karke dobara bolo: imaandaar reply aaya ("WiFi band hai")?

## SWITCH (sirf aapke apne networks)
5. Bolo "wifi password <aapke-network-ka-naam> ka <password>": "encrypted save ho gaya" aaya? (password galat length par, jaise 5 digit, mana karta hai?)
6. Bolo "best wifi pe jao": switch hua ya saaf reason aaya?
7. Bolo "wifi switch karo <naam>": hua? (Android 10+ par system ka confirm popup aaye to wo Android ka hai)
8. "wifi password <naam> bhool jao" — bhool gaya?

## OPEN NETWORK (sirf sach me khule network)
9. Kisi free/open WiFi (cafe/jio public) ke paas bolo "free wifi pe connect karo": juda ya saaf "nahi mila"? Password wale network par kabhi try nahi karta?

## QR
10. "wifi qr banao" (jude hue network ka): QR bana aur share sheet khula?
11. Wo QR (ya apne router ka QR) Gallery se NOVA me SHARE karo: Toast aaya "password encrypted save ho gaya"? Phir "wifi switch karo <naam>" chala?

## DOCTOR
12. Internet band karke (router off) bolo "wifi theek karo": saaf diagnosis aaya (router restart wala step)?
13. WiFi on par jude nahi: "internet nahi chal raha" — "kisi network se juda nahi" aaya?
14. Public WiFi ka login page wala network: "wifi theek karo" ne captive portal wala step bataya?

## GUARD (optional)
15. "wifi guard on karo" — ON hua? Phone ko WiFi weak jagah le jao (ya router band karo): 1-2 minute me aapke saved network pe switch kiya? Error log me "guard:" line aayi?
16. "wifi guard off karo" — OFF?

## PURANE ROUTE WAPAS (kuch nahi tootta)
17. "wifi on karo" / "wifi off karo" — pehle jaisa Android settings panel khulta hai (scan/switch kuch nahi karta)?
18. "wifi settings kholo" — settings page khulta hai?

Fail hone par: number + ek line kya hua + screenshot. Error aaye to Settings me AKHRI GALTIYAN > COPY karke bhejo.
