// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import io.github.ukpratik.starfolk.BuildConfig
import io.github.ukpratik.starfolk.R
import io.github.ukpratik.starfolk.core.content.Quest
import io.github.ukpratik.starfolk.core.sky.SkyLayout
import io.github.ukpratik.starfolk.core.time.AppDay
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import io.github.ukpratik.starfolk.ui.sky.StickerAtlas
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The share card (US-018): a 1080×1350 JPEG of the ticket on a strip of the user's sky, drawn off-screen with labels
 * OFF, so no star names, notes or moods can ever be in it. Saved to cacheDir/share and shared via FileProvider.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    suspend fun render(context: Context, quest: Quest, seed: Long, sky: List<CompletionLite>, today: AppDay): Uri = withContext(Dispatchers.Default) {
        val bmp = createBitmap(W, H)
        val c = Canvas(bmp)
        val scale = 3f
        // Night paper, top 55%.
        c.drawColor(0xFFF3EBDD.toInt())
        val skyH = H * 0.55f
        c.drawRect(0f, 0f, W.toFloat(), skyH, Paint().apply {
            shader = RadialGradient(W * 0.7f, skyH * 0.1f, W.toFloat(), intArrayOf(0xFF24336A.toInt(), 0xFF16213F.toInt(), 0xFF0F1730.toInt()), null, Shader.TileMode.CLAMP)
        })
        val model = SkyLayout.compute(seed, sky, today, W / scale, skyH / scale, maxStars = 10, maxStamps = 0, showClusters = false)
        val atlas = StickerAtlas.forDensity(scale)
        val star = atlas.star.asAndroidBitmap()
        for (s in model.dust) c.drawCircle(s.x * scale, s.y * scale, s.radius * scale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99F3EBDD.toInt() })
        for (s in model.starters + model.stars) {
            val size = 34f * scale * s.size
            c.withTranslation(s.x * scale, s.y * scale) { rotate(s.rotation); drawBitmap(star, null, RectF(-size / 2, -size / 2, size / 2, size / 2), null) }
        }
        // The ticket.
        val left = 80f; val top = skyH - 120f; val right = W - 80f; val bottom = H - 150f
        c.drawRoundRect(RectF(left + 6, top + 14, right + 6, bottom + 14), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        c.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFDF7.toInt() })
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCDBFA6.toInt(); strokeWidth = 5f }
        var y = top + 20; while (y < bottom - 20) { c.drawLine(left + 170, y, left + 170, y + 18, dash); y += 36 }
        val display = ResourcesCompat.getFont(context, R.font.bricolage_800)
        val body = ResourcesCompat.getFont(context, R.font.courier_prime_400)
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 34f; color = 0xFF1F5FCC.toInt(); letterSpacing = 0.2f }
        c.withTranslation(left + 105, bottom - 40) { rotate(-90f); drawText("ADMIT ONE · ${quest.realm.name}", 0f, 0f, label) }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 72f; color = 0xFF1D1B22.toInt() }
        val textWidth = (right - left - 230).toInt()
        val title = StaticLayout.Builder.obtain(quest.title, 0, quest.title.length, titlePaint, textWidth).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 0.95f).build()
        c.withTranslation(left + 210, top + 70) { title.draw(this) }
        val whyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; textSize = 36f; color = 0xFF4A4550.toInt() }
        val why = StaticLayout.Builder.obtain(quest.why, 0, quest.why.length, whyPaint, textWidth).build()
        c.withTranslation(left + 210, top + 90 + title.height) { why.draw(this) }
        // Footer.
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 44f; color = 0xFF1D1B22.toInt(); letterSpacing = 0.25f }
        c.drawText("STARFOLK", left, H - 70f, brand)
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = body; textSize = 32f; color = 0xFF4A4550.toInt() }
        c.drawText(context.getString(R.string.share_tagline), left + brand.measureText("STARFOLK") + 28f, H - 72f, tag)

        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "starfolk-quest.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }

    /** The invite text (US-018). Play links carry a referrer so shares can be counted (opt-in); F-Droid links don't. */
    fun text(context: Context, quest: Quest): String {
        val link = if (BuildConfig.ANALYTICS_AVAILABLE) "${BuildConfig.STORE_URL}&referrer=utm_source%3Dshare%26utm_content%3D${quest.id}" else BuildConfig.STORE_URL
        val lead = context.getString(if (quest.groupable) R.string.share_group else R.string.share_solo)
        return "$lead ${quest.title}\n👉 $link"
    }

    fun whatsAppInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(WHATSAPP, 0); true }.getOrDefault(false)

    fun intent(uri: Uri, text: String, toWhatsApp: Boolean): Intent {
        val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (toWhatsApp) send.setPackage(WHATSAPP) else Intent.createChooser(send, null)
    }

    private const val WHATSAPP = "com.whatsapp"
}
