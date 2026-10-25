// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.ContentRules
import io.github.ukpratik.starfolk.core.content.TrackId
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The packed assets the app ships (ADR-008) load, pass their checksums and still satisfy the content rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BundledContentSourceTest {
    private val source = BundledContentSource(ApplicationProvider.getApplicationContext<Application>().assets)

    @Test fun `the bundled content loads with every pack verified`() = runTest {
        val bundle = source.bundle()
        assertThat(bundle.quests).isNotEmpty()
        assertThat(bundle.tracks.map { it.track }).containsExactly(TrackId.BREAKUP, TrackId.WORK, TrackId.HEALTH)
        assertThat(bundle.sos.grounding).hasSize(5)
        assertThat(source.manifest().packs.map { it.id }).contains("helplines")
    }

    @Test fun `what ships has no content-rule errors`() = runTest {
        val issues = ContentRules.validate(source.bundle(), idsLock = allIds(), bannedPhrases = emptyList(), ContentRules.Mode.DRAFT, LocalDate.now())
        assertThat(issues.filter { it.severity == ContentRules.Severity.ERROR }).isEmpty()
    }

    private suspend fun allIds(): Set<String> {
        val b = source.bundle()
        return (b.quests.map { it.id } + b.tracks.flatMap { t -> t.days.map { it.id } } + b.sparks.map { it.id } +
            b.insights.map { it.id } + b.sos.templates.map { it.id } + b.sos.grounding.map { it.id }).toSet()
    }
}
