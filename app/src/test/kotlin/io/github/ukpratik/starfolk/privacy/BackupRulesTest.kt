// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.privacy

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * D-017 / LLD §2.4: private.db (moods, notes, star names, letters, WHO-5) must never reach cloud backup or device
 * transfer, on any API level. core.db and settings must, so the sky survives a phone change.
 */
class BackupRulesTest {
    private val privateFiles = listOf("private.db", "private.db-wal", "private.db-shm", "private.db-journal")

    private fun rules(file: String, section: String?): Pair<Set<String>, Set<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = if (section == null) doc.documentElement else doc.getElementsByTagName(section).item(0) as Element
        fun paths(tag: String) = root.getElementsByTagName(tag).let { list ->
            (0 until list.length).map { (list.item(it) as Element).let { e -> "${e.getAttribute("domain")}:${e.getAttribute("path")}" } }.toSet()
        }
        return paths("include") to paths("exclude")
    }

    /** An include-list: exactly core.db and the settings file. Nothing private, and no wildcard that could catch it. */
    private fun assertPrivateExcluded(file: String, section: String?) {
        val (include, _) = rules(file, section)
        assertThat(include).containsExactly("database:core.db", "file:datastore/settings.preferences_pb")
        for (p in privateFiles) assertThat(include).doesNotContain("database:$p")
    }

    @Test fun `API 30 and below`() = assertPrivateExcluded("backup_rules.xml", null)
    @Test fun `API 31+ cloud backup`() = assertPrivateExcluded("data_extraction_rules.xml", "cloud-backup")
    @Test fun `API 31+ device transfer`() = assertPrivateExcluded("data_extraction_rules.xml", "device-transfer")
}
