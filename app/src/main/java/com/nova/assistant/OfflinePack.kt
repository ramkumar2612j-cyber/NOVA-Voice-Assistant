package com.nova.assistant

/**
 * v6.96 "5 FREE OFFLINE AI": five extra on-device brains that need NO API key, NO account and NO internet after the download.
 * They are extra fall-back brains behind the existing Qwen2.5-0.5B slot: when that one is missing or cannot answer, the chat
 * tries these one by one (never two models in RAM at the same time).
 *
 * PURE Kotlin (no Android classes) so every rule below is unit tested. The network and file parts live in OfflinePackStore.
 *
 * Why the file name, size and SHA-256 are NOT written here: they could not be looked up from the build sandbox (no network).
 * Instead the app asks Hugging Face for the repo file list at download time (the API returns each file's real size and
 * LFS sha256), picks the Q4_K_M file with [pickFile], downloads it with the existing resumable downloader and refuses
 * the file when size or sha256 differ. A repo that was renamed or removed simply fails to resolve and is skipped.
 * Weak point (honest): the hash comes from the same host as the file, so it detects corrupt / truncated downloads,
 * not a hacked repo. Pin the hashes here later if you want that.
 */
object OfflinePack {
    const val T_CHATML = "chatml"
    const val T_LLAMA3 = "llama3"
    const val T_DEEPSEEK = "deepseek"

    private const val MB = 1024L * 1024L

    class Model(
        val id: String,
        val label: String,
        val maker: String,
        /** "CN" = Chinese lab, "World" = rest of the world. */
        val origin: String,
        val repo: String,
        /** The GGUF file to take from the repo's file list (case-insensitive). */
        val fileRegex: String,
        val template: String,
        val minRamMb: Long,
        /** Sanity range for the resolved file: anything outside is the wrong file and is refused. */
        val minBytes: Long,
        val maxBytes: Long
    )

    /**
     * Order = the order they are downloaded AND tried. Fast / small first, the slow "thinking" model last.
     * Only llama.cpp architectures that were already supported in 2024 are used (llama, qwen2, internlm2), because the bundled
     * llamacpp-kotlin 0.1.0 engine is old. Gemma 3 / Qwen3 are deliberately NOT in the list for that reason.
     */
    val MODELS: List<Model> = listOf(
        Model("qwen25-15b", "Qwen2.5 1.5B", "Alibaba", "CN", "Qwen/Qwen2.5-1.5B-Instruct-GGUF", "q4_k_m\\.gguf$", T_CHATML, 4000, 600 * MB, 1600 * MB),
        Model("llama32-1b", "Llama 3.2 1B", "Meta", "World", "bartowski/Llama-3.2-1B-Instruct-GGUF", "q4_k_m\\.gguf$", T_LLAMA3, 3000, 500 * MB, 1200 * MB),
        Model("internlm25-18b", "InternLM2.5 1.8B", "Shanghai AI Lab", "CN", "internlm/internlm2_5-1_8b-chat-gguf", "q4_k_m\\.gguf$", T_CHATML, 4000, 800 * MB, 1900 * MB),
        Model("smollm2-17b", "SmolLM2 1.7B", "Hugging Face", "World", "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF", "q4_k_m\\.gguf$", T_CHATML, 4000, 800 * MB, 1900 * MB),
        Model("deepseek-r1-15b", "DeepSeek-R1 Distill 1.5B", "DeepSeek", "CN", "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF", "q4_k_m\\.gguf$", T_DEEPSEEK, 5000, 800 * MB, 1600 * MB)
    )

    /**
     * v7.110 PART 7: brains that are NEVER downloaded by the automatic pack loop (they are not in [MODELS], so [toDownload] and
     * [runnable] ignore them). The owner starts them with a tap (BrainChoice). Qwen2.5-3B-Instruct Q4_K_M is about 2 GB.
     */
    val OWNER_PICK: List<Model> = listOf(
        Model("qwen25-3b", "Qwen2.5 3B", "Alibaba", "CN", "Qwen/Qwen2.5-3B-Instruct-GGUF", "q4_k_m\\.gguf$", T_CHATML, 6000, 1500 * MB, 2600 * MB)
    )

    fun byId(id: String): Model? = MODELS.firstOrNull { it.id == id } ?: OWNER_PICK.firstOrNull { it.id == id }

    /** A file picked from the repo list. */
    class PackFile(val path: String, val size: Long, val sha256: String)

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    /**
     * [treeJson] = body of https://huggingface.co/api/models/{repo}/tree/main (a JSON array of {type, path, size, lfs:{oid,size}}).
     * Returns the first root-level file that matches the model's regex, has a real 64-hex LFS sha256, is not a split part
     * and has a size inside the model's sanity range. null = nothing usable (the model is skipped, never guessed).
     */
    fun pickFile(treeJson: String, m: Model): PackFile? {
        val root = MiniJson.parse(treeJson) as? List<*> ?: return null
        val re = Regex(m.fileRegex, RegexOption.IGNORE_CASE)
        for (e in root) {
            if (MiniJson.field(e, "type") != "file") continue
            val path = MiniJson.field(e, "path") as? String ?: continue
            if (path.contains('/') || path.contains("..")) continue
            if (!re.containsMatchIn(path)) continue
            if (path.contains("-of-", ignoreCase = true)) continue   // split GGUF part, not a whole model
            val lfs = MiniJson.field(e, "lfs")
            val sha = (MiniJson.field(lfs, "oid") as? String ?: "").lowercase()
            if (!HEX64.matches(sha)) continue
            val size = ((MiniJson.field(lfs, "size") ?: MiniJson.field(e, "size")) as? Double)?.toLong() ?: continue
            if (size < m.minBytes || size > m.maxBytes) continue
            return PackFile(path, size, sha)
        }
        return null
    }

    fun downloadUrl(m: Model, file: PackFile): String = "https://huggingface.co/" + m.repo + "/resolve/main/" + file.path

    fun treeUrl(m: Model): String = "https://huggingface.co/api/models/" + m.repo + "/tree/main"

    /** Space that must stay free AFTER the download, so the phone never runs out of storage because of NOVA. */
    const val KEEP_FREE_BYTES = 1536L * MB

    /**
     * What still has to be downloaded right now, in order. Empty when there is nothing to do or the conditions are not met.
     * Big files go only over an unmetered network (Wi-Fi) and only when the phone has the RAM and the storage.
     */
    fun toDownload(installed: Set<String>, skipped: Set<String>, totalRamMb: Long, freeBytes: Long, unmetered: Boolean, online: Boolean): List<Model> {
        if (!online || !unmetered) return emptyList()
        return MODELS.filter { it.id !in installed && it.id !in skipped && totalRamMb >= it.minRamMb && freeBytes >= it.maxBytes + KEEP_FREE_BYTES }
    }

    /** Models the phone can ever run (RAM only), for the status line. */
    fun runnable(totalRamMb: Long): List<Model> = MODELS.filter { totalRamMb >= it.minRamMb }

    /** Installed models in try-order. [installed] = id -> path of files that exist. */
    fun tryOrder(installed: Map<String, String>): List<Pair<Model, String>> =
        OWNER_PICK.mapNotNull { m -> installed[m.id]?.let { Pair(m, it) } } +   // v7.110: an owner-picked bigger brain is tried first
            MODELS.mapNotNull { m -> installed[m.id]?.let { Pair(m, it) } }

    /** One short status sentence for the self-test log. */
    fun statusLine(installed: Set<String>, totalRamMb: Long): String {
        val can = runnable(totalRamMb)
        val have = can.count { it.id in installed }
        return "Offline AI pack: $have/${can.size} ready (" + can.joinToString(", ") { (if (it.id in installed) "ON " else "-- ") + it.label } + ")"
    }
}

/** Turns the app's ChatML prompt (LocalChat.buildPrompt) into the template each model family was trained on. */
object PackPrompt {
    private val BLOCK = Regex("(?s)<\\|im_start\\|>(system|user|assistant)\\n(.*?)(?:<\\|im_end\\|>|$)")

    private const val DS_USER = "<\uFF5CUser\uFF5C>"
    private const val DS_ASSISTANT = "<\uFF5CAssistant\uFF5C>"

    /**
     * ChatML stays as it is. Llama 3 and DeepSeek get their own markers. The BOS token is NOT written as text: llama.cpp adds it
     * itself, and a second one hurts quality. (Not verified on a phone: if answers look odd, check this first.)
     */
    fun convert(chatml: String, template: String): String {
        if (template == OfflinePack.T_CHATML || !chatml.contains("<|im_start|>")) return chatml
        val blocks = BLOCK.findAll(chatml).map { Pair(it.groupValues[1], it.groupValues[2].trim()) }.toList()
        if (blocks.isEmpty()) return chatml
        val sb = StringBuilder()
        when (template) {
            OfflinePack.T_LLAMA3 -> {
                for ((role, text) in blocks) {
                    if (text.isEmpty()) continue
                    sb.append("<|start_header_id|>").append(role).append("<|end_header_id|>\n\n").append(text).append("<|eot_id|>")
                }
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            OfflinePack.T_DEEPSEEK -> {
                val system = blocks.filter { it.first == "system" }.joinToString("\n") { it.second }
                sb.append(system)
                for ((role, text) in blocks) {
                    if (text.isEmpty() || role == "system") continue
                    sb.append(if (role == "user") DS_USER else DS_ASSISTANT).append(text)
                    if (role == "assistant") sb.append("\uFF5Cend\u2581of\u2581sentence\uFF5C")
                }
                sb.append(DS_ASSISTANT)
            }
            else -> return chatml
        }
        return sb.toString()
    }
}

/** Cleans raw text from any of the pack models before LocalChat.clean (which only knows the ChatML end marker). */
object PackText {
    private val STOPS = listOf(
        "<|eot_id|>", "<|end_of_text|>", "<|im_end|>", "<end_of_turn>", "</s>",
        "<\uFF5Cend\u2581of\u2581sentence\uFF5C>", "\uFF5Cend\u2581of\u2581sentence\uFF5C", "<|endoftext|>"
    )

    /** null = nothing usable (empty, or the model was still "thinking" when it was cut off). */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        var s = raw
        s = s.replace(Regex("(?s)<think>.*?</think>"), "")
        if (s.contains("<think>")) return null   // thinking never finished: no answer
        var cut = s.length
        for (st in STOPS) {
            val i = s.indexOf(st)
            if (i in 0 until cut) cut = i
        }
        s = s.substring(0, cut).trim()
        return if (s.isEmpty()) null else s
    }
}
