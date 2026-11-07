// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.tools

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.starfolk.core.content.ContentJson
import io.github.ukpratik.starfolk.core.content.Manifest
import java.io.File
import java.nio.file.Files
import org.junit.Test

/** Runs the CLI against the real content/v1 (path from the build, see build.gradle.kts). */
class ContentCliTest {
    private val content = File(System.getProperty("starfolk.content"))

    private fun run(vararg args: String): Triple<Int, List<String>, List<String>> {
        val out = mutableListOf<String>(); val err = mutableListOf<String>()
        val code = run(args.toList(), { out += it }, { err += it })
        return Triple(code, out, err)
    }

    @Test fun `the committed content passes draft validation`() {
        val (code, out, err) = run("validate", content.path, "--date=2026-10-25")
        assertThat(err).isEmpty()
        assertThat(code).isEqualTo(0)
        assertThat(out.last()).startsWith("draft validation: 0 error(s)")
    }

    @Test fun `release validation fails until reviews are done and while helpline checks are stale`() {
        // A build date well past the 90-day window: every helpline stamp is stale by then.
        val (code, _, err) = run("validate", content.path, "--release", "--date=2027-12-31")
        assertThat(code).isEqualTo(1)
        assertThat(err.any { "reviewed_by" in it }).isTrue()
        assertThat(err.any { "more than 90 days" in it }).isTrue()
    }

    @Test fun `pack is deterministic and the manifest hashes match the files`() {
        val a = Files.createTempDirectory("packA").toFile()
        val b = Files.createTempDirectory("packB").toFile()
        assertThat(run("pack", content.path, a.path).first).isEqualTo(0)
        assertThat(run("pack", content.path, b.path).first).isEqualTo(0)
        val manifest = ContentJson.decodeFromString(Manifest.serializer(), a.resolve("manifest.json").readText())
        assertThat(manifest.packs.map { it.id }).containsAtLeast("quests", "shelter-breakup", "shelter-work", "shelter-health", "sparks", "insights", "helplines", "sos")
        for (p in manifest.packs) {
            val text = a.resolve(p.file).readText()
            assertThat(Packer.sha256(text)).isEqualTo(p.sha256)
            assertThat(b.resolve(p.file).readText()).isEqualTo(text)
        }
    }

    @Test fun `signing and verifying turn release validation green for those items`() {
        val copy = Files.createTempDirectory("content").toFile()
        content.parentFile.copyRecursively(copy)
        val v1 = copy.resolve("v1")
        assertThat(run("sign-review", v1.path, "--by=Dr Test, PsyD", "--on=2026-10-20", "all").first).isEqualTo(0)
        for (cc in listOf("US", "CA", "GB", "IE", "AU", "NZ", "IN", "PH", "NG", "ZA", "SG", "KE")) run("verify-helpline", v1.path, cc, "--on=2026-10-20")
        val (_, _, err) = run("validate", v1.path, "--release", "--date=2026-10-25")
        // Only the two countries without verified lines remain (NG, KE): a content task, not a review one.
        assertThat(err.filter { "reviewed_by" in it || "not verified" in it }).isEmpty()
        assertThat(err.all { "helpline:NG" in it || "helpline:KE" in it }).isTrue()
        val pack = Files.createTempFile("pack", ".md").toFile()
        assertThat(run("review-pack", v1.path, pack.path).first).isEqualTo(0)
        assertThat(pack.readText()).contains("s-breakup-21")
    }

    @Test fun `bad usage exits 2`() {
        assertThat(run().first).isEqualTo(2)
        assertThat(run("explode", content.path).first).isEqualTo(2)
    }
}
