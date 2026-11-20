package com.dantech.splitvibe.designsystem.tokens

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fonts and their licences are files in this module, not dependencies, so Gradle's dependency
 * verification never sees them. `font-checksums.sha256` records the SHA-256 of each one, as fetched
 * from its upstream repository at a pinned commit; the `fonts` job of `checksums.yml` downloads them
 * again on a clean machine and compares. This test checks that the files here are those files.
 */
class FontChecksumsTest {

    private val listed: Map<String, String> = File(CHECKSUMS)
        .readLines()
        .filter { it.isNotBlank() }
        .associate { line ->
            val (hash, path) = line.trim().split(Regex("\\s+"), limit = 2)
            path to hash
        }

    @Test
    fun `every bundled font and licence is the file the checksum list names`() {
        val differing = listed.filter { (path, hash) -> sha256(File(path)) != hash }.keys
        assertTrue("Files that differ from $CHECKSUMS, or are missing: $differing", differing.isEmpty())
    }

    @Test
    fun `the checksum list covers every bundled font and licence`() {
        val fonts = File(FONTS).listFiles().orEmpty().map { "$FONTS/${it.name}" }
        val unlisted = (fonts + LICENCES).filterNot { it in listed }
        assertTrue("Files with no entry in $CHECKSUMS: $unlisted", unlisted.isEmpty())
    }

    private fun sha256(file: File): String? {
        if (!file.isFile) return null
        return MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** From `core/designsystem`, where the module's tests run. */
        const val CHECKSUMS = "font-checksums.sha256"
        const val FONTS = "src/main/res/font"
        val LICENCES = listOf("src/main/res/raw/bricolage_grotesque_ofl.txt", "src/main/res/raw/be_vietnam_pro_ofl.txt")
    }
}
