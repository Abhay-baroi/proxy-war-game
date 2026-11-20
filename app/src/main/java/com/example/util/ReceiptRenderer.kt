package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.PlayerRole
import com.example.model.RoundRevealData
import java.io.File
import java.io.FileOutputStream

object ReceiptRenderer {

    fun renderReceiptBitmap(
        reveal: RoundRevealData,
        roomCode: String,
        isMatchEnd: Boolean
    ): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.parseColor("#090D16"),
                    Color.parseColor("#0F172A"),
                    Color.parseColor("#090D16")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle cyber grid lines
        val gridPaint = Paint().apply {
            color = Color.parseColor("#182235")
            strokeWidth = 2f
        }
        var gx = 0f
        while (gx <= width) {
            canvas.drawLine(gx, 0f, gx, height.toFloat(), gridPaint)
            gx += 90f
        }
        var gy = 0f
        while (gy <= height) {
            canvas.drawLine(0f, gy, width.toFloat(), gy, gridPaint)
            gy += 90f
        }

        // Top Neon Header Strip
        val accentColor = if (reveal.humansWonRound) Color.parseColor("#06B6D4") else Color.parseColor("#E11D48")
        val stripPaint = Paint().apply { color = accentColor }
        canvas.drawRect(0f, 0f, width.toFloat(), 20f, stripPaint)

        // Brand Header
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 68f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText("PROXY WARS", 72f, 130f, titlePaint)

        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22D3EE")
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.14f
        }
        val tagText = if (isMatchEnd) {
            "FINAL MATCH RECEIPT  •  ROOM $roomCode"
        } else {
            "ROUND ${reveal.roundNum}/${reveal.totalRounds} RECEIPT  •  ROOM $roomCode"
        }
        canvas.drawText(tagText, 72f, 185f, subtitlePaint)

        // Outcome Badge Card
        val badgeRect = RectF(72f, 235f, width - 72f, 345f)
        val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (reveal.humansWonRound) Color.parseColor("#083344") else Color.parseColor("#4C0519")
        }
        val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = accentColor
        }
        canvas.drawRoundRect(badgeRect, 24f, 24f, badgeBg)
        canvas.drawRoundRect(badgeRect, 24f, 24f, badgeStroke)

        val outcomePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val outcomeLabel = if (reveal.humansWonRound) {
            "HUMANS DETECTED THE GLITCH"
        } else {
            "THE GLITCH EVADED CAPTURE"
        }
        canvas.drawText(outcomeLabel, 110f, 304f, outcomePaint)

        // Prompt Card
        val promptRect = RectF(72f, 385f, width - 72f, 645f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
        }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#28354E")
        }
        canvas.drawRoundRect(promptRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(promptRect, 28f, 28f, cardStroke)

        val sectionLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("PROMPT • ${reveal.category.uppercase()}", 112f, 445f, sectionLabelPaint)

        val promptBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawWrappedText(canvas, reveal.promptText, promptBodyPaint, 112f, 475f, width - 224)

        // Top Voted Bluff / Highlighted Glitch Answer
        val topBluff = reveal.revealedSubmissions.maxByOrNull { it.voteCount }
            ?: reveal.revealedSubmissions.firstOrNull()
        val bluffRect = RectF(72f, 685f, width - 72f, 1020f)
        canvas.drawRoundRect(bluffRect, 28f, 28f, cardPaint)
        val bluffStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#F59E0B")
        }
        canvas.drawRoundRect(bluffRect, 28f, 28f, bluffStroke)

        val amberLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F59E0B")
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("MOST SUSPECTED RESPONSE (${topBluff?.voteCount ?: 0} VOTES)", 112f, 745f, amberLabelPaint)

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val quoteText = topBluff?.let { "\"${it.text}\"" } ?: "\"No responses logged.\""
        drawWrappedText(canvas, quoteText, quotePaint, 112f, 775f, width - 224)

        val authorRevealPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (topBluff?.role == PlayerRole.GLITCH) Color.parseColor("#FB7185") else Color.parseColor("#22D3EE")
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val roleWord = if (topBluff?.role == PlayerRole.GLITCH) "THE GLITCH" else "HUMAN"
        canvas.drawText("SUBMITTED BY ${topBluff?.authorNickname?.uppercase() ?: "UNKNOWN"} ($roleWord)", 112f, 975f, authorRevealPaint)

        // Unmasked Glitches + Standings
        val boardRect = RectF(72f, 1060f, width - 72f, 1730f)
        canvas.drawRoundRect(boardRect, 28f, 28f, cardPaint)
        canvas.drawRoundRect(boardRect, 28f, 28f, cardStroke)

        val leaderTitle = if (isMatchEnd) "TABLE CHAMPION & FINAL STANDINGS" else "LIVE LEADERBOARD"
        canvas.drawText(leaderTitle, 112f, 1120f, sectionLabelPaint)

        val champion = reveal.scoreDeltas.firstOrNull()
        if (champion != null) {
            val champPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#10B981")
                textSize = 44f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val prefix = if (isMatchEnd) "CHAMPION: " else "LEADING: "
            canvas.drawText("$prefix${champion.nickname} (${champion.totalScore} PTS)", 112f, 1185f, champPaint)
        }

        val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = 33f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowMetaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 27f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        reveal.scoreDeltas.take(6).forEachIndexed { idx, entry ->
            val yPos = 1265f + idx * 72f
            val roleTag = if (entry.role == PlayerRole.GLITCH) "[GLITCH]" else "[HUMAN]"
            canvas.drawText("${idx + 1}. ${entry.nickname}", 112f, yPos, rowPaint)
            val deltaStr = if (entry.deltaPoints > 0) "+${entry.deltaPoints}" else "+0"
            canvas.drawText("$roleTag  $deltaStr  •  ${entry.totalScore} PTS", 540f, yPos, rowMetaPaint)
        }

        // Footer Hashtags
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#06B6D4")
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("#ProxyWars   #WhosTheBot   #CampusBreak", 72f, 1835f, footerPaint)

        return bitmap
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        maxWidth: Int
    ) {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.1f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    fun shareReceiptBitmap(context: Context, bitmap: Bitmap, roomCode: String) {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "proxy_wars_${roomCode}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Proxy Wars: Who's the Bot? Room $roomCode #ProxyWars #WhosTheBot")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share 9:16 Match Receipt")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
