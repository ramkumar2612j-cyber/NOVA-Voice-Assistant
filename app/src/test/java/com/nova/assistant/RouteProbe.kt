package com.nova.assistant

import java.time.LocalDate

/**
 * Part 2 (v7.133) audit helper: replays the ORDER of the pure (no Android) text routes of NovaService.handle() and returns the name of the
 * first one that would fire, or "AI" when nothing local owns the sentence. Routes that need a Context (appControl, mediaRoute side effects,
 * brain shortcuts, offline knowledge) are listed by their parser only, so the answer is "the parser says yes", never "it worked".
 */
object RouteProbe {
    fun route(raw: String): String {
        val text = VoiceText.normalize(raw, canonicalizeAsrAliases = true)
        if (text.isBlank()) return "blank"
        fun <T> t(f: () -> T?): T? = try { f() } catch (e: Throwable) { null }
        if (t { TeachRules.isIntent(text) } == true) return "teach"
        val k = t { Logic.classify(text) }
        if (k != null && PersonalMemory.isMemoryKind(k.kind)) return "memory:" + k.kind
        if (t { RecordingVoiceRules.parse(text) } != null) return "recording_manager_voice"
        if (t { BackgroundVideoRules.parse(text) } != null) return "video_rec"
        if (t { RecordRules.parse(text) } != null) return "rec"
        if (t { ResultCardRules.isReadAll(text) } == true) return "read_all"
        if (t { CameraEyeRules.parse(text) } != null) return "camera_eye"
        if (t { TradeRules.parse(text) } != null) return "trade_analysis"
        if (t { FamilyContacts.parseSave(text) } != null) return "family_save"
        if (t { ReelRules.isWatchOff(text) } == true) return "reel_watch_off"
        if (t { ShortsOpenRules.parse(text) } != null) return "shorts_open"
        if (t { ReelRules.parse(text, false) } != null) return "reel_plan"
        if (t { ReelOpenRules.parse(text) } != null) return "reel_open"
        if (t { ProfileSearchRules.parse(text) } != null) return "profile_search"
        if (t { MovieScanRules.parse(text) } == MovieScanRules.Ask.MOVIE) return "movie_scan"
        if (t { ScreenVideoRules.isBareAnalyse(text) } == true && t { AnalysisVoiceRules.parse(text) } == AnalysisVoiceRules.Ask.NONE) return "analyze_screen"
        if (t { ScreenVideoRules.isScreenVideoAsk(text) } == true) return "screen_video"
        if (t { AnalysisVoiceRules.parse(text) } != AnalysisVoiceRules.Ask.NONE) return "analyse_media"
        if (t { MediaIntake.voiceIntent(text) } != MediaIntake.Voice.NONE) return "media_intake"
        if (t { AgentTaskRules.isResumeQuery(text) } == true) return "agent_resume"
        if (t { AgentTaskRules.isHistoryQuery(text) } == true) return "agent_history"
        if (t { ConfigReport.isQuery(text) } == true) return "config_report"
        if (t { AgentTaskRules.isStatusQuery(text) } == true) return "agent_status"
        if (t { AutoTrace.isAsk(text) } == true) return "auto_trace"
        if (t { FeatureVerify.isAsk(text) } == true) return "auto_verify"
        if (t { SiteIntent.parse(text) } == null && t { HelpGuide.isAsk(text) } == true) return "help_guide"
        if (t { FeatureVoice.key(text) } != null) return "feature_status"
        if (t { SourceMap.isAsk(text) } == true) return "source_map"
        if (t { LocalSkills.answer(text, LocalDate.of(2026, 10, 29), "hi") } != null) return "local_skill"
        if (t { LeadRules.isLeadAcquisitionRequest(text) } == true) return "lead_help"
        if (t { SitePreviewRules.isPreview(text) } == true) return "site_preview"
        if (t { PublishTarget.parse(text) } != null) return "publish_target"
        if (t { SiteIntent.isPublish(text) } == true) return "publish"
        if (t { ConnectorRules.parsePlan(text) } != null) return "connector_plan"
        if (t { ConnectorRules.parse(text) } != null) return "connector"
        if (t { LearnRules.parse(text) } != null) return "learn"
        if (t { MediaIntent.classify(text) } != null) return "media_route"
        if (t { SiteIntent.parse(text) } != null) return "site"
        if (t { ImageIntent.parse(text) } != null) return "image"
        val auto = t { AutoIntent.detect(text, false) }
        if (auto != null && auto.kind == AutoIntent.Kind.SITE) return "auto_site"
        if (auto != null && auto.kind == AutoIntent.Kind.IMAGE) return "auto_image"
        if (t { MusicPlayRulesPlus.parseBest(text) } != null) return "music_play"
        if (t { GmailRules.parse(text) } != null) return "gmail"
        if (t { UnreadQueryRules.parse(text) } != null) return "unread_query"
        if (t { AppSearchRules.parse(text) } != null) return "app_control"
        if (t { OpenWebRules.parseOpen(text) } != null) return "web_open"
        if (t { OpenWebRules.parseDownload(text) } != null) return "app_download"
        if (t { AgentRules.isTask(text) } == true) return "agent"
        val kk = k?.kind
        if (t { RoundaboutRules.needsWho(Logic.norm(text)) } == true && (kk == null || kk == "call")) return "clarify_who"
        if (k == null) { val h = t { RoundaboutRules.hint(Logic.norm(text)) }; if (h != null) return "hint:" + h.kind }
        val plan = t { CommandRouter.plan(text) }
        if (plan?.needsClarification == true && k == null && t { WhatsAppCommandRules.parse(text) } == null) return "intent_clarification"
        val ei = plan?.intent
        if (ei?.kind == IntentParser.Kind.APP_MESSAGE && ei.appId != null) return "whatsapp_send(app:" + ei.appId + ")"
        if (ei?.kind == IntentParser.Kind.APP_CALL) return "whatsapp_call_unsupported"
        if (t { WhatsAppCommandRules.parse(text) } != null) return "whatsapp_send"
        val c0 = t { Understand.classify(text) }
        if (c0 != null) return "local:" + c0.kind + (if (c0.arg.isNotEmpty()) ":" + c0.arg.take(24) else "")
        val web = t { WebSources.classify(text) }
        if (web != null) return "web_" + web.kind.name.lowercase()
        return "AI"
    }
}
