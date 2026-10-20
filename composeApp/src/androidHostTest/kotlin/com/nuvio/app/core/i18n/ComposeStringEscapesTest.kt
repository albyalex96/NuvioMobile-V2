package com.nuvio.app.core.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Compose Multiplatform resources, unlike Android `res/`, do not unescape `\'` and `\"`: the
 * backslash is shown to the user. Plain `'` and `"` are valid inside the XML text instead.
 * Checked for the locales maintained by this fork (English and Italian).
 */
class ComposeStringEscapesTest {
    @Test
    fun stringsDoNotUseAndroidQuoteEscapes() {
        val resources = locateResourcesDir()
        val offenders = listOf("values", "values-it")
            .flatMap { locale -> File(resources, locale).listFiles { file -> file.extension == "xml" }.orEmpty().toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if ("\\'" in line || "\\\"" in line) "${file.parentFile.name}/${file.name}:${index + 1}" else null
                }
            }
        if (offenders.isNotEmpty()) {
            fail("Use plain ' and \" in composeResources strings (escapes are shown literally):\n" + offenders.joinToString("\n"))
        }
    }

    private fun locateResourcesDir(): File {
        val relative = "src/commonMain/composeResources"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            listOf(File(dir, relative), File(dir, "composeApp/$relative"))
                .firstOrNull { it.isDirectory }
                ?.let { return it }
            dir = dir.parentFile
        }
        error("Cannot locate $relative from ${File("").absolutePath}")
    }
}
