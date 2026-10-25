// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.content

/**
 * Where the app reads content from (ADR-008). v1: the bundled packs. v2 adds a remote static feed with the same
 * manifest format, where the newest pack version wins.
 */
interface ContentSource {
    suspend fun manifest(): Manifest
    suspend fun bundle(): ContentBundle
}
