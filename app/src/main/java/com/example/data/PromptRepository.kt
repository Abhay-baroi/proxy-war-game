package com.example.data

import android.content.Context
import com.example.model.PromptItem
import org.json.JSONArray

class PromptRepository(private val context: Context) {

    private var cachedPrompts: List<PromptItem>? = null

    fun loadAllPrompts(): List<PromptItem> {
        cachedPrompts?.let { return it }
        val jsonText = context.assets.open("prompts.json").bufferedReader().use { it.readText() }
        val array = JSONArray(jsonText)
        val items = ArrayList<PromptItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val linesArr = obj.getJSONArray("aiLines")
            val lines = List(linesArr.length()) { idx -> linesArr.getString(idx) }
            items.add(
                PromptItem(
                    id = obj.getString("id"),
                    category = obj.getString("category"),
                    prompt = obj.getString("prompt"),
                    aiLines = lines
                )
            )
        }
        cachedPrompts = items
        return items
    }

    fun pickMatchPrompts(count: Int = 3): List<PromptItem> {
        val all = loadAllPrompts()
        return all.shuffled().take(count.coerceAtMost(all.size))
    }

    fun buildRerollAiLines(prompt: PromptItem, currentLines: List<String>): List<String> {
        val freshFromSame = prompt.aiLines.filter { it !in currentLines }
        val pool = mutableListOf<String>()
        pool.addAll(freshFromSame)

        if (pool.size < 3) {
            val genericBackup = listOf(
                "Optimal operational efficiency required adjusting my standard routine slightly.",
                "Unforeseen logistical variables impacted my original execution schedule.",
                "I prioritized maintaining consistent quality metrics over rapid completion.",
                "Empirical campus data suggests this outcome occurs periodically."
            ).filter { it !in currentLines && it !in pool }
            pool.addAll(genericBackup)
        }

        while (pool.size < 3 && prompt.aiLines.isNotEmpty()) {
            val candidate = prompt.aiLines.random()
            if (candidate !in pool) pool.add(candidate) else break
        }
        return pool.take(3)
    }
}
