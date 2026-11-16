#!/usr/bin/env python3
"""v7.185: FREE photo EDIT fallback (Gemini fail/missing -> Hugging Face free edit -> phone-only). Static checks only."""
import sys
R='app/src/main/java/com/nova/assistant/'
f=open(R+'FreeImageEdit.kt',encoding='utf-8').read()
ma=open(R+'MainActivity.kt',encoding='utf-8').read()
chk={
 'token only via SecureStore hf slot, never logged':'SecureStore.getSlot(ctx, SLOT)' in f and 'val SLOT = "hf"' in f.replace('private const val SLOT = "hf"','val SLOT = "hf"') and all(x not in f for x in ('Log.d(','Log.e(','println(key')),
 'reuses FreeImage toggle, no new switch added':'FreeImage.enabled(' in ma and 'FREE IMAGE FALLBACK' in f,
 'input photo size capped before sending':'MAX_BYTES_IN' in f and 'srcPhoto.size > FreeImageEditRules.MAX_BYTES_IN' in f,
 'output validated as a real image before use':'fun valid(' in f and 'BitmapFactory' in f,
 'editAi tries free edit only after Gemini fails, never instead of it':'if (r.ok && img != null) { editFinish(true, "AI se ho gaya", img, 2); return@Thread }' in ma and 'FreeImageEdit.run(' in ma,
 'no-Gemini-key path still shows its own HAAN dialog before sending to HF':'Photo free AI (Hugging Face) ko bhejein?' in ma and 'HAAN, BHEJO' in ma,
 'honest: exact HF request shape flagged as unverified':'NOT phone/CI tested' in f or 'was never reachable' in f,
 'phone-only (Level 1) still the final fallback either way':'editPhone(pe, src' in ma and 'editPhone(pe, cur' in ma,
}
bad=[k for k,v in chk.items() if not v]
for k,v in chk.items(): print(('PASS' if v else 'FAIL')+': '+k)
sys.exit(1 if bad else 0)
