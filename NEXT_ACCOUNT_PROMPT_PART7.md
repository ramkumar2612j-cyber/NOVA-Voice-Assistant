# NOVA Part 7 (naye account ke liye)
Base: NOVA-WIP-v75-PART6.zip. Pehle padho: PART6_STATUS.md, PART5_STATUS.md. Owner Hinglish bolta hai, sirf phone se kaam karta hai. Jo ho chuka use rewrite mat karo. Strict rules wahi (kuch delete/disable nahi, HAAN/NAHI confirmation nahi hatana, keys log nahi, NOT TESTED honestly likho).
Pehle: GitHub Actions ka build log mile to errors fix. Local compile: `bash $PWD/tools/local_compile_check.sh /tmp/nova_check` (org.json ke 25 tests sandbox mein fail dikhte hain: normal).
Baaki kaam:
1. Asli phone par check (owner se): bina bulaye popup band hua? 5s speed (Feature Status > latency row)? Quality=SMOOTH se atak gaya? Website quality kaisi?
2. Agar website abhi bhi sadharan: SiteGen mein 2-pass (pehle structure, phir polish) ya section-by-section generation; premium gate aur sakht karo.
3. Voice se mobile ke aur kaam (AgentRules/PhoneActionRules ke tools badhao) sirf maujooda confirmation ke saath.
4. Bolkar ON/OFF (status ke baad) sirf confirmation ke saath.
5. Aakhir mein: PART7_STATUS.md, Hindi status, zip, NEXT_ACCOUNT_PROMPT_PART8.md.
