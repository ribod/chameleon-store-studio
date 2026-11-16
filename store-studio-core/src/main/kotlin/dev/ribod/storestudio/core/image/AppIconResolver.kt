package dev.ribod.storestudio.core.image

import java.io.File

/**
 * Finds the app icon of each platform by convention only. Anything unconventional is meant to be
 * configured explicitly (`storeStudio { icons { } }`), so there is no fuzzy name matching.
 */
public object AppIconResolver {

    private val densities = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
    private val skipDirs =
        setOf("build", ".gradle", ".git", ".idea", "node_modules", "Pods", "DerivedData")

    // shortcut: walks at most MAX_DEPTH levels below each root, deeper icons must be configured
    private const val MAX_DEPTH = 7

    /**
     * Android launcher icon: the Play Store icon Android Studio generates (`ic_launcher-playstore.png`),
     * else `mipmap-<density>/ic_launcher.png|webp` at the highest density. First root with a match wins.
     */
    public fun findAndroid(roots: List<File>): File? = roots.firstNotNullOfOrNull { androidIn(it) }

    /** iOS icon: the largest PNG of `AppIcon.appiconset`. First root with a match wins. */
    public fun findIos(roots: List<File>): File? = roots.firstNotNullOfOrNull { iosIn(it) }

    private fun androidIn(root: File): File? {
        val found = mutableListOf<File>()
        walk(root) { dir ->
            File(dir, "ic_launcher-playstore.png").takeIf { it.isFile }?.let(found::add)
            if (dir.name.startsWith("mipmap-")) {
                for (ext in listOf("png", "webp")) {
                    File(dir, "ic_launcher.$ext").takeIf { it.isFile }?.let(found::add)
                }
            }
        }
        return found.minWithOrNull(
            compareBy<File> { it.name != "ic_launcher-playstore.png" }
                .thenBy { f ->
                    densities.indexOfFirst { f.parentFile.name.endsWith(it) }.takeIf { it >= 0 }
                        ?: 99
                }
                .thenBy { it.extension == "webp" },
        )
    }

    private fun iosIn(root: File): File? {
        var best: File? = null
        walk(root) { dir ->
            if (dir.name == "AppIcon.appiconset") {
                // The largest file is the 1024px marketing icon
                val candidate = dir.listFiles { f -> f.isFile && f.extension.equals("png", true) }
                    .orEmpty().maxByOrNull { it.length() }
                if (candidate != null &&
                    candidate.length() > (best?.length() ?: -1)
                ) {
                    best = candidate
                }
            }
        }
        return best
    }

    private fun walk(dir: File, depth: Int = 0, visit: (File) -> Unit) {
        visit(dir)
        if (depth >= MAX_DEPTH) return
        dir.listFiles { f ->
            f.isDirectory && f.name !in skipDirs
        }?.forEach { walk(it, depth + 1, visit) }
    }
}
