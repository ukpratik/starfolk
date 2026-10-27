// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.share

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.StarfolkApp
import io.github.ukpratik.starfolk.core.content.Realm
import io.github.ukpratik.starfolk.core.sky.CompletionLite
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-018: a 1080×1350 card from cacheDir/share via FileProvider, and an invite link with the referrer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareCardTest {
    private val app = ApplicationProvider.getApplicationContext<StarfolkApp>()

    @Test fun `renders the card and builds the invite`() = runBlocking {
        val quest = app.container.content.bundle().quests.first { it.groupable }
        val today = app.container.appDayClock.today()
        val uri = ShareCard.render(app, quest, 7L, 0, listOf(CompletionLite(1, Realm.CONNECT, today)), today)
        assertThat(uri.authority).isEqualTo("${app.packageName}.share")
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
        assertThat(opts.outWidth to opts.outHeight).isEqualTo(1080 to 1350)
        val text = ShareCard.text(app, quest)
        assertThat(text).contains(quest.title)
        assertThat(text).contains("Who's in?")
        // Play links carry the referrer (US-018); the F-Droid build links to its F-Droid page with no tracking (D-028).
        if (io.github.ukpratik.starfolk.BuildConfig.ANALYTICS_AVAILABLE) assertThat(text).contains("utm_source%3Dshare%26utm_content%3D${quest.id}")
        else assertThat(text).contains("f-droid.org/packages/")
    }
}
