package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** README heading of the section that lists every published patch as a two column table. */
internal const val README_PATCHES_HEADING = "## Patches"

/**
 * Parses the README patch table into `name to headline description` rows, in README order.
 *
 * Every published patch is documented as one `| <name> | <headline> |` row, where `<headline>` is
 * the first (zh-Hant) line of the patch's bilingual description; the table is kept in sync with
 * `patches-list.json`.
 *
 * The checks below fail loudly when the section or its table disappears, so restructuring the
 * README can never silently degrade these contract tests into no-ops again.
 */
internal fun readmePatchRows(): Map<String, String> {
    val readme = Files.readString(
        readmeRepositoryRoot().resolve("README.md"),
        StandardCharsets.UTF_8,
    )
    val section = readme.substringAfter(README_PATCHES_HEADING, "")
    assertTrue(
        "README.md must keep a '$README_PATCHES_HEADING' section listing the published patches",
        section.isNotEmpty(),
    )

    val rows = section
        .substringBefore("\n## ")
        .lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.startsWith("|") }
        .mapNotNull { line -> readmePatchRow(line) }
        .toList()

    assertTrue(
        "README '$README_PATCHES_HEADING' section must contain a patch table",
        rows.isNotEmpty(),
    )
    assertEquals(
        "README patch table must list every published patch exactly once",
        GboardPublishedPatchCatalog.morpheRegistrations.size,
        rows.size,
    )
    assertEquals(
        "README patch table must not repeat a patch name",
        rows.size,
        rows.map { row -> row.first }.distinct().size,
    )

    return rows.toMap()
}

/**
 * Asserts the README documents the patch named [name] with the headline line of its bilingual
 * [description].
 */
internal fun assertReadmeListsPatch(name: String?, description: String?) {
    val patchName = checkNotNull(name) { "Patch name must not be null" }
    val headline = checkNotNull(description) { "Patch '$patchName' must declare a description" }
        .lineSequence()
        .first()
        .trim()
    assertTrue(
        "Patch '$patchName' must declare a non-blank headline description",
        headline.isNotEmpty(),
    )

    val rows = readmePatchRows()
    assertTrue("README patch table must list '$patchName'", rows.containsKey(patchName))
    assertEquals("README headline description for '$patchName'", headline, rows.getValue(patchName))
}

/** Reads one `| <name> | <headline> |` row, skipping the header and the column separator. */
private fun readmePatchRow(line: String): Pair<String, String>? {
    val cells = line
        .removePrefix("|")
        .removeSuffix("|")
        .split("|")
        .map { cell -> cell.trim() }
    if (cells.size != 2) return null

    val name = cells[0]
    if (name.isEmpty()) return null
    if (name.all { character -> character == '-' || character == ':' }) return null
    if (name == "Patch") return null

    return name to cells[1]
}

private fun readmeRepositoryRoot(): Path {
    val workingDirectory = Path.of("").toAbsolutePath().normalize()
    return generateSequence(workingDirectory) { it.parent }
        .firstOrNull { candidate -> Files.isRegularFile(candidate.resolve("settings.gradle.kts")) }
        ?: error("Could not locate repository root from $workingDirectory")
}
