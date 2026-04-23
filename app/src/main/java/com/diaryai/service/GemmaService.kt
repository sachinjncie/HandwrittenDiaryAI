package com.diaryai.service

import android.content.Context
import com.diaryai.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GemmaService – wraps the on-device Gemma 4 inference layer.
 *
 * Integration path (production):
 *   Use ML Kit GenAI APIs (com.google.mlkit:genai-inference) or MediaPipe LLM Inference API
 *   with a Gemma 4 .task file bundled in assets/ or downloaded at first run.
 *
 *   Model file: assets/gemma4_q4.task  (~1-4 GB, downloaded at onboarding)
 *   API: com.google.mediapipe.tasks.genai.llminference.LlmInference
 *
 * Current state: rule-based stubs that produce realistic outputs for testing.
 * Replace runInference() with the real LlmInference.generateResponse() call
 * once the model file is available on-device.
 */
@Singleton
class GemmaService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    // ── Model lifecycle ──────────────────────────────────────────────────────

    var isModelLoaded: Boolean = false
        private set

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        // TODO: initialize LlmInference with assets/gemma4_q4.task
        // val options = LlmInference.LlmInferenceOptions.builder()
        //     .setModelPath("/data/local/tmp/gemma4.task")
        //     .setMaxTokens(1024)
        //     .setTopK(40)
        //     .setTemperature(0.8f)
        //     .setRandomSeed(101)
        //     .build()
        // llmInference = LlmInference.createFromOptions(context, options)
        isModelLoaded = true
        true
    }

    // ── OCR correction ───────────────────────────────────────────────────────

    suspend fun correctOcrText(rawText: String): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext rawText
        val prompt = buildCorrectionPrompt(rawText)
        runInference(prompt).let { parseCorrection(it, rawText) }
    }

    // ── Task extraction ──────────────────────────────────────────────────────

    suspend fun extractTasks(text: String, sessionId: String): List<TaskItem> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        val prompt = buildTaskExtractionPrompt(text)
        val json = runInference(prompt)
        parseTasksJson(json, sessionId)
    }

    // ── Knowledge extraction ─────────────────────────────────────────────────

    suspend fun extractKnowledge(text: String, sessionId: String): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) return@withContext emptyList()
        val prompt = buildKnowledgePrompt(text)
        val json = runInference(prompt)
        parseKnowledgeJson(json, sessionId)
    }

    // ── Recall / related notes ────────────────────────────────────────────────

    suspend fun findRelatedNotes(text: String, existingEntries: List<KnowledgeEntry>): List<String> =
        withContext(Dispatchers.IO) {
            if (existingEntries.isEmpty() || !isModelLoaded) return@withContext emptyList()
            // Simple keyword overlap recall (replace with embedding similarity when available)
            val words = text.lowercase().split(Regex("\\W+")).filter { it.length > 4 }.toSet()
            existingEntries.filter { entry ->
                val entryWords = (entry.title + " " + entry.tags + " " + entry.summary)
                    .lowercase().split(Regex("\\W+")).toSet()
                (words intersect entryWords).size >= 2
            }.map { it.id }.take(5)
        }

    // ── Internal inference call ───────────────────────────────────────────────

    private suspend fun runInference(prompt: String): String {
        // TODO: replace with real LlmInference.generateResponse(prompt)
        return simulateInference(prompt)
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private fun buildCorrectionPrompt(rawText: String) = """
        You are a handwriting OCR correction assistant. Correct spelling, fix OCR artifacts,
        preserve the original meaning and intent exactly. Return ONLY the corrected text, nothing else.
        
        OCR TEXT:
        $rawText
    """.trimIndent()

    private fun buildTaskExtractionPrompt(text: String) = """
        Extract all action items, todos, and tasks from the diary text below.
        Return a JSON array with this exact schema:
        [{"title":"string","description":"string","dueDateHint":"string or null","priority":"LOW|MEDIUM|HIGH|CRITICAL","confidence":0.0-1.0,"tags":"comma,separated","projectHint":"string or null"}]
        Return ONLY valid JSON, no explanation.
        
        TEXT:
        $text
    """.trimIndent()

    private fun buildKnowledgePrompt(text: String) = """
        Extract knowledge entries, facts, learnings, and insights from the diary text.
        Return a JSON array with this exact schema:
        [{"title":"string","summary":"string","body":"string","tags":"comma,separated","category":"string"}]
        Return ONLY valid JSON, no explanation.
        
        TEXT:
        $text
    """.trimIndent()

    // ── Response parsers ──────────────────────────────────────────────────────

    private fun parseCorrection(response: String, fallback: String): String =
        response.trim().ifBlank { fallback }

    private fun parseTasksJson(json: String, sessionId: String): List<TaskItem> = try {
        val raw = extractJsonArray(json)
        val arr = gson.fromJson(raw, JsonArray::class.java)
        arr.map { el ->
            val m = el.asJsonObject
            TaskItem(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                title = m["title"]?.asString ?: "",
                description = m["description"]?.asString ?: "",
                dueDateHint = m["dueDateHint"]?.asString,
                priority = runCatching { TaskPriority.valueOf(m["priority"]?.asString ?: "MEDIUM") }.getOrDefault(TaskPriority.MEDIUM),
                confidence = m["confidence"]?.asFloat ?: 0.8f,
                tags = m["tags"]?.asString ?: "",
                projectHint = m["projectHint"]?.asString
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun parseKnowledgeJson(json: String, sessionId: String): List<KnowledgeEntry> = try {
        val raw = extractJsonArray(json)
        val arr = gson.fromJson(raw, JsonArray::class.java)
        arr.map { el ->
            val m = el.asJsonObject
            KnowledgeEntry(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                title = m["title"]?.asString ?: "",
                summary = m["summary"]?.asString ?: "",
                body = m["body"]?.asString ?: "",
                tags = m["tags"]?.asString ?: "",
                category = m["category"]?.asString ?: "general"
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else "[]"
    }

    // ── Simulation (stub, replace with real model) ────────────────────────────

    private fun simulateInference(prompt: String): String {
        return when {
            prompt.contains("OCR TEXT") ->
                prompt.substringAfter("OCR TEXT:\n").trim()
                    .replace("0", "o").replace("1", "l") // simulate minor fixes
            prompt.contains("Extract all action") ->
                """[{"title":"Review meeting notes","description":"Go through the notes from today's meeting","dueDateHint":"tomorrow","priority":"HIGH","confidence":0.9,"tags":"work,meeting","projectHint":null}]"""
            prompt.contains("Extract knowledge") ->
                """[{"title":"Key insight from diary","summary":"Important reflection noted today","body":"Detailed knowledge body extracted from diary entry.","tags":"reflection,learning","category":"personal"}]"""
            else -> ""
        }
    }
}
