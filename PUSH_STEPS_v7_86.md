# v7.86 push (Termux / terminal)
cd ~/NOVA-Voice-Assistant            # apna repo folder
git pull origin main
# zip ko ~/storage/downloads me rakho, phir:
unzip -o ~/storage/downloads/NOVA-v7_86-final.zip -d /tmp/n786
cp -r /tmp/n786/nova/. .             # .github folder bhi copy hota hai
git add -A
git commit -m "v7.86: popup / laser / samajh-nahi-aaya loop fixes + Screen Scan card"
git push origin main
# phir GitHub > Actions > "Build and Test APK" > Run workflow
