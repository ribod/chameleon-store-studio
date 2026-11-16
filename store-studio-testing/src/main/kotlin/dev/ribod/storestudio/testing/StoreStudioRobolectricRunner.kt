package dev.ribod.storestudio.testing

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.LocaleList
import android.text.TextUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.ribod.storestudio.model.FailedJobSpec
import dev.ribod.storestudio.model.FailedJobsManifest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.util.Locale

@Serializable
public data class RobolectricJobResult(
    public val jobId: String,
    public val success: Boolean,
    public val errorMessage: String? = null,
)

@Serializable
public data class RobolectricExecutionResults(public val results: List<RobolectricJobResult>)

/**
 * Headless Robolectric Native Graphics runner for failed Composable screenshot jobs (DD-05 / ADR-001).
 *
 * Emulates full Android hardware acceleration via Skia Native Graphics, provides isolated
 * per-job localization, multi-device exact-pixel viewport layout, and Compose testing synchronization.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
public class StoreStudioRobolectricRunner {

    private val composeRule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<ComponentActivity>,
        ComponentActivity,
        > = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    public val ruleChain: RuleChain = RuleChain
        .outerRule(
            TestRule { base, _ ->
                object : Statement() {
                    override fun evaluate() {
                        val app = ApplicationProvider.getApplicationContext<Application>()
                        val pm = app.packageManager
                        val shadowPm = Shadows.shadowOf(pm)
                        val cn =
                            ComponentName(app.packageName, "androidx.activity.ComponentActivity")
                        shadowPm.addActivityIfNotPresent(cn)
                        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                        }
                        shadowPm.addIntentFilterForActivity(cn, filter)
                        base.evaluate()
                    }
                }
            },
        )
        .around(composeRule)

    @Test
    public fun renderFailedJobs() {
        val failedJobsFilePath = System.getProperty("storestudio.failedJobsFile").orEmpty()
        Assume.assumeTrue(
            "StoreStudioRobolectricRunner: no failedJobsFile specified via system property",
            failedJobsFilePath.isNotBlank(),
        )
        val failedJobsFile = File(failedJobsFilePath)
        Assume.assumeTrue(
            "StoreStudioRobolectricRunner: failedJobsFile '$failedJobsFilePath' does not exist",
            failedJobsFile.isFile,
        )

        val manifest = FailedJobsManifest.decodeFromString(failedJobsFile.readText())
        if (manifest.jobs.isEmpty()) {
            println("StoreStudioRobolectricRunner: failedJobsFile is empty (0 jobs to render).")
            return
        }

        var currentJob by mutableStateOf<FailedJobSpec?>(null)

        composeRule.setContent {
            val job = currentJob
            if (job != null) {
                key(job.jobId) {
                    StoreRobolectricWrapper(job)
                }
            }
        }

        val results = mutableListOf<RobolectricJobResult>()
        val originalLocale = Locale.getDefault()
        val originalLocales = LocaleList.getDefault()
        try {
            for (job in manifest.jobs) {
                // Compose's Locale.current (used by StoreScreenshotCanvas to identify the job's
                // locale) and locale-sensitive formatting read the process default, not only the
                // Configuration the wrapper provides
                val locale = parseLocale(job.localeTag.ifBlank { job.storeLocale })
                Locale.setDefault(locale)
                LocaleList.setDefault(LocaleList(locale))
                val result = renderSingleJob(job) { currentJob = it }
                results.add(result)
            }
        } finally {
            Locale.setDefault(originalLocale)
            LocaleList.setDefault(originalLocales)
        }

        val resultsFilePath = System.getProperty("storestudio.resultsFile")
        if (!resultsFilePath.isNullOrBlank()) {
            val resultsFile = File(resultsFilePath)
            resultsFile.parentFile?.mkdirs()
            val json = Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
            resultsFile.writeText(
                json.encodeToString(
                    RobolectricExecutionResults.serializer(),
                    RobolectricExecutionResults(results),
                ),
            )
        }
    }

    private fun renderSingleJob(
        job: FailedJobSpec,
        setCurrentJob: (FailedJobSpec?) -> Unit,
    ): RobolectricJobResult = try {
        composeRule.runOnUiThread<Unit> {
            setCurrentJob(job)
        }

        try {
            composeRule.waitForIdle()
        } catch (_: Exception) {
            // Previews with infinite animations (rememberInfiniteTransition, CircularProgressIndicator, etc.)
            // never reach Compose's idle state; waitForIdle times out with AppNotIdleException after advancing
            // frames, so the current frame is still valid to capture.
        }

        val activity = composeRule.activity
        val decorView = activity.window.decorView

        // Measure & layout decorView to exact physical dimensions required by target device slot
        val widthSpec = View.MeasureSpec.makeMeasureSpec(job.widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(
            job.heightPx,
            View.MeasureSpec.EXACTLY,
        )
        composeRule.runOnUiThread<Unit> {
            decorView.measure(widthSpec, heightSpec)
            decorView.layout(0, 0, job.widthPx, job.heightPx)
        }

        // Draw hardware-accelerated Skia RNG frame into a Bitmap
        val bitmap = Bitmap.createBitmap(job.widthPx, job.heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeRule.runOnUiThread<Unit> {
            decorView.draw(canvas)
        }

        // Write PNG to output path
        val outputFile = File(job.outputPath)
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        RobolectricJobResult(
            jobId = job.jobId,
            success = true,
        )
    } catch (t: Throwable) {
        t.printStackTrace()
        RobolectricJobResult(
            jobId = job.jobId,
            success = false,
            errorMessage = t.message ?: t.javaClass.simpleName,
        )
    }

    @Composable
    private fun StoreRobolectricWrapper(job: FailedJobSpec) {
        val context = LocalContext.current
        val baseConfig = LocalConfiguration.current

        // 1. Compose Multiplatform Res.* support
        ConfigureCmpResourcesIfPresent()

        // 2. Localized Context & Density configuration per job
        val configuredConfig = remember(job) {
            Configuration(baseConfig).apply {
                val locale = parseLocale(job.localeTag.ifBlank { job.storeLocale })
                setLocales(LocaleList(locale))
                densityDpi = job.dpi
                screenWidthDp = job.widthDp
                screenHeightDp = job.heightDp
                smallestScreenWidthDp = minOf(job.widthDp, job.heightDp)
            }
        }
        val configuredContext = remember(configuredConfig) {
            context.createConfigurationContext(configuredConfig)
        }
        val density = remember(job.dpi) {
            Density(density = job.dpi / 160f, fontScale = 1f)
        }
        val layoutDir = remember(job.localeTag) {
            val loc = parseLocale(job.localeTag.ifBlank { job.storeLocale })
            if (TextUtils.getLayoutDirectionFromLocale(loc) == View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }
        }

        CompositionLocalProvider(
            LocalContext provides configuredContext,
            LocalConfiguration provides configuredContext.resources.configuration,
            LocalDensity provides density,
            LocalLayoutDirection provides layoutDir,
            LocalInspectionMode provides false, // Real runtime mode in Robolectric
        ) {
            Box(modifier = Modifier.requiredSize(job.widthDp.dp, job.heightDp.dp)) {
                InvokeComposableScreenshot(job.methodFqn)
            }
        }
    }

    @Composable
    private fun ConfigureCmpResourcesIfPresent() {
        val method = remember {
            runCatching {
                Class.forName("org.jetbrains.compose.resources.AndroidContextProviderKt")
                    .methods.firstOrNull { it.name == "PreviewContextConfigurationEffect" }
            }.getOrNull()
        }
        if (method != null) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                val composer = currentComposer
                runCatching {
                    method.invoke(null, composer, 0)
                }
            }
        }
    }

    @Composable
    private fun InvokeComposableScreenshot(methodFqn: String) {
        val className = methodFqn.substringBeforeLast('.')
        val methodName = methodFqn.substringAfterLast('.').substringBefore('$')
        val composer = currentComposer

        // Only the lookup is guarded: an exception from the composable itself must not trigger a second call
        val invokeMethod = runCatching {
            Class.forName("androidx.compose.ui.tooling.ComposableInvoker")
                .methods.firstOrNull { it.name == "invokeComposable" }
        }.getOrNull()

        if (invokeMethod != null) {
            try {
                invokeMethod.invoke(null, className, methodName, composer, emptyArray<Any?>())
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        } else {
            val cls = Class.forName(className)
            val method = cls.methods.firstOrNull { it.name == methodName }
                ?: error("Could not find @Composable method '$methodName' in '$className'")
            val paramTypes = method.parameterTypes
            val args = arrayOfNulls<Any?>(paramTypes.size)
            for (i in paramTypes.indices) {
                when (paramTypes[i]) {
                    androidx.compose.runtime.Composer::class.java -> args[i] = composer
                    Int::class.javaPrimitiveType -> args[i] = 0
                    else -> args[i] = null
                }
            }
            method.invoke(null, *args)
        }
    }

    private fun parseLocale(tag: String): Locale {
        val clean = tag.replace("-r", "-").replace('_', '-')
        return Locale.forLanguageTag(clean)
    }
}
