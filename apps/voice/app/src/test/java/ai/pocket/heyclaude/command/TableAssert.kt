package ai.pocket.heyclaude.command

import org.junit.Assert.assertTrue

/** Runs every row of a table and reports all mismatches at once. */
internal fun <T> assertTable(cases: List<Pair<String, T>>, fn: (String) -> T) {
    val failures = cases.mapNotNull { (input, want) ->
        val got = fn(input)
        if (got == want) null else "  \"$input\": want $want, got $got"
    }
    assertTrue(
        "${failures.size} of ${cases.size} rows failed:\n" + failures.joinToString("\n"),
        failures.isEmpty(),
    )
}
