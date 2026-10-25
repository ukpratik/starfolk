// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.tools

import io.github.ukpratik.starfolk.core.content.ContentRules
import io.github.ukpratik.starfolk.core.content.Level
import io.github.ukpratik.starfolk.core.content.Realm
import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

private const val USAGE = """starfolk-content <command>
  validate <dir> [--release] [--date=YYYY-MM-DD]   check content against the rules (LLD §3.2)
  pack <dir> <out>                                 write asset packs + manifest.json
  report <dir>                                     counts by realm × level and by track
  lock <dir>                                       append new ids to content/ids.lock (ids are never reused)
  review-pack <dir> <out.md>                       Shelter + SOS as one document for the expert review (US-026)
  sign-review <dir> --by="Name, credential" --on=YYYY-MM-DD <ids…|all>   stamp reviewed_by/on on reviewed items
  verify-helpline <dir> <CC> --on=YYYY-MM-DD         stamp verified_on after checking a country's numbers"""

fun main(args: Array<String>) {
    val code = try {
        run(args.toList(), System.out::println, System.err::println)
    } catch (e: ContentLoader.LoadError) {
        System.err.println("error: ${e.message}"); 1
    }
    exitProcess(code)
}

/** Returns the exit code. Separate from [main] so tests can call it. */
fun run(args: List<String>, out: (String) -> Unit, err: (String) -> Unit): Int {
    val command = args.firstOrNull() ?: return 2.also { err(USAGE) }
    val dir = args.getOrNull(1)?.let(::File) ?: return 2.also { err(USAGE) }
    val loader = ContentLoader(dir)
    return when (command) {
        "validate" -> {
            val mode = if ("--release" in args) ContentRules.Mode.RELEASE else ContentRules.Mode.DRAFT
            val date = args.firstOrNull { it.startsWith("--date=") }?.substringAfter('=')?.let(LocalDate::parse) ?: LocalDate.now()
            val issues = ContentRules.validate(loader.load(), loader.idsLock().toSet(), loader.bannedPhrases(), mode, date)
            issues.sortedWith(compareBy({ it.severity }, { it.itemId })).forEach { if (it.severity == ContentRules.Severity.ERROR) err(it.toString()) else out(it.toString()) }
            val errors = issues.count { it.severity == ContentRules.Severity.ERROR }
            out("${mode.name.lowercase()} validation: $errors error(s), ${issues.size - errors} warning(s)")
            if (errors > 0) 1 else 0
        }
        "pack" -> {
            val target = args.getOrNull(2)?.let(::File) ?: return 2.also { err(USAGE) }
            val manifest = Packer.pack(loader.load(), loader.meta(), target)
            out("packed ${manifest.packs.size} packs (content ${manifest.contentVersion}) into $target")
            0
        }
        "report" -> {
            val b = loader.load()
            out("realm      " + Level.entries.joinToString("") { it.name.lowercase().padEnd(10) })
            for (r in Realm.entries) out(r.name.lowercase().padEnd(11) + Level.entries.joinToString("") { l -> b.quests.count { it.realm == r && it.level == l }.toString().padEnd(10) })
            b.tracks.forEach { out("track ${it.track.name.lowercase()}: ${it.days.size}/${it.length} days, ${it.days.count { d -> d.reviewedBy != null }} reviewed") }
            out("sparks ${b.sparks.size} · insights ${b.insights.size} · helplines ${b.helplines.size} countries (${b.helplines.count { it.verifiedOn != null }} verified) · sos ${b.sos.templates.size} templates, ${b.sos.grounding.size} grounding steps")
            0
        }
        "lock" -> {
            val b = loader.load()
            val lockFile = dir.resolve("../ids.lock")
            val existing = loader.idsLock().toSet()
            val ids = b.quests.map { it.id } + b.tracks.flatMap { t -> t.days.map { it.id } } + b.sparks.map { it.id } +
                b.insights.map { it.id } + b.sos.templates.map { it.id } + b.sos.grounding.map { it.id }
            val added = ids.filter { it !in existing }.distinct().sorted()
            if (added.isNotEmpty()) lockFile.appendText(added.joinToString("\n", postfix = "\n"))
            out("added ${added.size} id(s) to ids.lock")
            0
        }
        "review-pack" -> {
            val target = args.getOrNull(2)?.let(::File) ?: return 2.also { err(USAGE) }
            target.writeText(ReviewTools.reviewPack(loader.load()))
            out("wrote $target"); 0
        }
        "sign-review" -> {
            val by = args.firstOrNull { it.startsWith("--by=") }?.substringAfter('=') ?: return 2.also { err("--by is required") }
            val on = args.firstOrNull { it.startsWith("--on=") }?.substringAfter('=')?.let(LocalDate::parse) ?: return 2.also { err("--on is required") }
            val ids = args.drop(2).filterNot { it.startsWith("--") }.toSet()
            if (ids.isEmpty()) return 2.also { err("list ids, or 'all'") }
            out("signed ${ReviewTools.sign(dir, ids, by, on)} item(s)"); 0
        }
        "verify-helpline" -> {
            val cc = args.getOrNull(2)?.uppercase() ?: return 2.also { err(USAGE) }
            val on = args.firstOrNull { it.startsWith("--on=") }?.substringAfter('=')?.let(LocalDate::parse) ?: return 2.also { err("--on is required") }
            if (ReviewTools.verifyHelpline(dir, cc, on)) { out("$cc verified on $on"); 0 } else { err("no entry for $cc"); 1 }
        }
        else -> 2.also { err(USAGE) }
    }
}
