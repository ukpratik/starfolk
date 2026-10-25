// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.starfolk.core.content

import kotlinx.serialization.json.Json

/** The one JSON configuration for content: strict (unknown fields are an error, LLD §3.2 rule 1). */
val ContentJson: Json = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    prettyPrint = false
}
