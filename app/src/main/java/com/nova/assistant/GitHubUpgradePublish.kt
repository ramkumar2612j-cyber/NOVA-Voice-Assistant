package com.nova.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.184 PART B (ii): upgrade branch push + GitHub Actions build poll.
 * IMPORTANT HONEST LIMIT: this object does NOT write or change any .kt file, and it does NOT ask an AI to generate a
 * Kotlin patch. It only (a) creates a branch "jarvis-up-<id>" from main and (b) drops a plain note file on that branch
 * saying what the owner asked for, then (c) can poll the latest Actions run for that branch. The step that actually
 * turns the wish into a code change (an AI writing a Kotlin patch against a 300-file app) is NOT built: the owner's
 * own handover doc flags this as the single biggest risk ("AI ka Kotlin patch bina compile ke 300-file app me tootne
 * ka bada chance hai"), so it needs its own separate HAAN-gated design, not a quiet add here.
 * Evidence level: written + statically checked only. No network here (sandbox), so never compiled or run.
 */
object GitHubUpgradePublish {
    class Out(val ok: Boolean, val msg: String = "")
    class PollOut(val status: String, val conclusion: String = "", val artifactUrl: String = "", val msg: String = "")

    private const val API = "https://api.github.com"
    // Owner's repo, confirmed by the owner 2026-11-16. If the owner ever renames/forks it, this one line changes
    // (GitHubSelfBuild.kt reuses this same constant so there is only one place to update).
    internal const val OWNER_REPO = "ramkumar2612j-cyber/JARVIS-Voice-Assistant"

    private fun call(token: String, method: String, path: String, body: String?): Pair<Int, String> {
        return try {
            val c = URL(API + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000; c.readTimeout = 40000
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "JARVIS-assistant")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            Pair(code, text)
        } catch (e: java.net.UnknownHostException) { Pair(598, "") }
        catch (e: Exception) { Pair(599, "") }
    }

    private fun failFor(code: Int): String = when (code) {
        401 -> "GitHub token galat ya expire ho gaya. Settings mein naya token daalo"
        403, 404 -> "GitHub ne mana kiya. Token mein repo ka permission (scope: repo) chahiye"
        422 -> "Branch pehle se ho sakta hai"
        598 -> "Internet nahi mil raha"
        else -> "Abhi push nahi ho paya. Thodi der baad dobara try karo"
    }

    /** Branch "jarvis-up-<id>" banata hai main se, aur usme sirf ek NOTE file daalta hai. Koi app .kt file nahi chhuta. */
    fun pushBranch(ctx: Context, id: Int, wishText: String): Out {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) return Out(false, "GitHub token nahi mila. Settings mein daalo")
        val branch = UpgradeRules.tag(id)
        val mainRef = call(token, "GET", "/repos/$OWNER_REPO/git/ref/heads/main", null)
        if (mainRef.first !in 200..299) return Out(false, failFor(mainRef.first))
        val sha = try { JSONObject(mainRef.second).getJSONObject("object").getString("sha") } catch (e: Exception) { "" }
        if (sha.isEmpty()) return Out(false, failFor(599))
        val mk = call(token, "POST", "/repos/$OWNER_REPO/git/refs", JSONObject().put("ref", "refs/heads/" + branch).put("sha", sha).toString())
        if (mk.first !in 200..299 && mk.first != 422) return Out(false, failFor(mk.first))
        val note = "# JARVIS upgrade request #" + id + "\n\n" + wishText.take(600) +
            "\n\nStatus: branch bani, abhi koi code patch nahi hai (agla step, alag se HAAN ke saath).\n"
        val b64 = Base64.encodeToString(note.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val put = call(token, "PUT", "/repos/$OWNER_REPO/contents/UPGRADE_REQUEST_" + id + ".md",
            JSONObject().put("message", "JARVIS upgrade #" + id + ": note").put("content", b64).put("branch", branch).toString())
        if (put.first !in 200..299) return Out(false, failFor(put.first))
        return Out(true, "Branch " + branch + " bana, note push ho gaya")
    }

    /** Us upgrade branch ke sabse naye Actions run ki status. Koi push nahi karta, sirf padhta hai. */
    fun pollLatestRun(ctx: Context, id: Int): PollOut {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) return PollOut("error", "", "", "GitHub token nahi mila")
        val branch = UpgradeRules.tag(id)
        val r = call(token, "GET", "/repos/$OWNER_REPO/actions/runs?branch=" + branch + "&per_page=1", null)
        if (r.first !in 200..299) return PollOut("error", "", "", failFor(r.first))
        val runs = try { JSONObject(r.second).getJSONArray("workflow_runs") } catch (e: Exception) { JSONArray() }
        if (runs.length() == 0) return PollOut("not_started", "", "", "Is branch par abhi koi build shuru nahi hua")
        val run = runs.getJSONObject(0)
        val status = run.optString("status")          // queued | in_progress | completed
        val conclusion = run.optString("conclusion")   // success | failure | "" (null)
        if (status == "completed" && conclusion == "success") {
            val art = call(token, "GET", "/repos/$OWNER_REPO/actions/runs/" + run.optLong("id") + "/artifacts", null)
            val url = try { JSONObject(art.second).getJSONArray("artifacts").getJSONObject(0).getString("archive_download_url") } catch (e: Exception) { "" }
            return PollOut(status, conclusion, url, "Build green")
        }
        return PollOut(status, conclusion, "", "")
    }
}
