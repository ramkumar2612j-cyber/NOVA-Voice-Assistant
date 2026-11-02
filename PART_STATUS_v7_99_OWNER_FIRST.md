# v7.99 - OWNER FIRST (AI does not talk over the owner)
ADDED: ReplyPresenceRules (25 s wait, seen / owner-reply detection, 15 min / 3 min takeover, chat context for the AI), listener onNotificationRemoved + historyOf, SKILLS / MADAD / CAPS entry.
RUN: Kotlin compile 0 errors, JUnit 1468/1468 (11 new), python guards 146/146 (one guard text relaxed: smartAutoReply call now has extra arguments).
NOT TESTED on phone: needs Message mode ON for the app + Notification access + AI chain. Android shows only the notification (no blue ticks).
Phone test: 1) friend sends a message, open the chat within 25 s -> AI sends nothing, history says "Aapne message dekh liya". 2) friend sends, you answer from the notification -> no AI reply. 3) friend sends, wait 25 s -> AI replies in your style. 4) you write a message, friend answers within 15 min -> AI stays out.
