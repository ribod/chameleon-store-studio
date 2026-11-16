package dev.ribod.storestudio.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreFeatureGraphic
import dev.ribod.storestudio.annotations.StoreScreenshot
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.EntryKind
import dev.ribod.storestudio.model.StoreManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Proxy

class StoreStudioSymbolProcessorTest {

    @Test
    fun validScreenshotProducesCorrectManifest() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(
            codeGenerator = codeGenerator,
            logger = logger,
            modulePath = ":feature:onboarding",
            projectRootDir = "/app/project",
        )

        val file = fakeFile(
            "OnboardingScreen.kt",
            "/app/project/feature/onboarding/src/OnboardingScreen.kt",
        )
        val function = fakeFunction(
            name = "HeroScreenshot",
            packageName = "com.example.onboarding",
            file = file,
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(
                        fakeArgument("id", "onboarding_hero"),
                        fakeArgument("order", 1),
                        fakeArgument("name", "Track habits daily"),
                        fakeArgument("devices", listOf("PHONE")),
                        fakeArgument("targets", listOf("PLAY_STORE")),
                        fakeArgument("locales", listOf("en-US")),
                    ),
                ),
            ),
        )

        val resolver = fakeResolver(screenshots = listOf(function))
        processor.process(resolver)
        processor.finish()

        assertEquals("No errors should be logged: ${logger.errors}", 0, logger.errors.size)
        val manifestStream = codeGenerator.createdFiles["META-INF/store-studio/store-manifest.json"]
        assertNotNull("Manifest file must be generated", manifestStream)

        val manifestJson = manifestStream!!.toString(Charsets.UTF_8.name())
        val manifest = StoreManifest.decodeFromString(manifestJson)

        assertEquals(":feature:onboarding", manifest.module)
        assertEquals(1, manifest.entries.size)

        val entry = manifest.entries.first()
        assertEquals(EntryKind.SCREENSHOT, entry.kind)
        assertEquals("onboarding_hero", entry.id)
        assertEquals(1, entry.order)
        assertEquals("Track habits daily", entry.title)
        assertEquals("com.example.onboarding.OnboardingScreenKt.HeroScreenshot", entry.functionFqn)
        assertEquals("com.example.onboarding", entry.packageName)
        assertEquals("HeroScreenshot", entry.functionName)
        assertEquals(listOf(DeviceFamily.PHONE), entry.devices)
        assertEquals(listOf(StoreTarget.PLAY_STORE), entry.targets)
        assertEquals(listOf("en-US"), entry.locales)
        assertEquals("feature/onboarding/src/OnboardingScreen.kt", entry.source.path)
    }

    @Test
    fun validFeatureGraphicProducesCorrectManifest() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(
            codeGenerator = codeGenerator,
            logger = logger,
            modulePath = ":app",
            projectRootDir = "/app/project",
        )

        val file = fakeFile("Banner.kt", "/app/project/app/src/Banner.kt")
        val function = fakeFunction(
            name = "PromoBanner",
            packageName = "com.example.promo",
            file = file,
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreFeatureGraphic",
                    qualifiedName = StoreFeatureGraphic::class.qualifiedName!!,
                    arguments = listOf(
                        fakeArgument("id", "promo_banner"),
                        fakeArgument("name", "Store Feature Graphic"),
                        fakeArgument("locales", listOf("en-US", "es-MX")),
                    ),
                ),
            ),
        )

        val resolver = fakeResolver(features = listOf(function))
        processor.process(resolver)
        processor.finish()

        assertEquals(0, logger.errors.size)
        val manifestStream = codeGenerator.createdFiles["META-INF/store-studio/store-manifest.json"]
        assertNotNull(manifestStream)

        val manifest = StoreManifest.decodeFromString(
            manifestStream!!.toString(Charsets.UTF_8.name()),
        )
        val entry = manifest.entries.first()
        assertEquals(EntryKind.FEATURE_GRAPHIC, entry.kind)
        assertEquals("promo_banner", entry.id)
        assertEquals(0, entry.order)
        assertEquals(listOf(StoreTarget.PLAY_STORE), entry.targets)
        assertEquals(listOf("en-US", "es-MX"), entry.locales)
    }

    @Test
    fun rejectsClassMemberFunction() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val parentClass = fakeClass("MyScreen")
        val function = fakeFunction(
            name = "Hero",
            parent = parentClass,
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("must be a top-level function") })
    }

    @Test
    fun rejectsFunctionWithParameters() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val param = fakeParam("count")
        val function = fakeFunction(
            name = "Hero",
            params = listOf(param),
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("must have 0 parameters") })
    }

    @Test
    fun rejectsPrivateFunction() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val function = fakeFunction(
            name = "Hero",
            modifiers = setOf(Modifier.PRIVATE),
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("cannot be private") })
    }

    @Test
    fun rejectsNonComposableFunction() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val function = fakeFunction(
            name = "Hero",
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("must be annotated with @Composable") })
    }

    @Test
    fun rejectsLookalikeComposableAnnotation() {
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(FakeCodeGenerator(), logger, ":app", "/root")

        val function = fakeFunction(
            name = "Hero",
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "com.example.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))
        processor.finish()

        assertTrue(logger.errors.any { it.contains("must be annotated with @Composable") })
    }

    @Test
    fun rejectsInvalidIdCharacters() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val function = fakeFunction(
            name = "Hero",
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "Hero Screen!")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("Invalid @StoreScreenshot id 'Hero Screen!'") })
    }

    @Test
    fun rejectsNegativeOrder() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val function = fakeFunction(
            name = "Hero",
            file = fakeFile("Screen.kt", "/root/Screen.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "hero"), fakeArgument("order", -1)),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(function)))

        processor.finish()

        assertTrue(logger.errors.any { it.contains("Invalid order '-1'") })
    }

    @Test
    fun collectsScreenshotsFromAllRoundsIntoOneManifest() {
        val codeGenerator = FakeCodeGenerator()
        val processor = StoreStudioSymbolProcessor(codeGenerator, FakeLogger(), ":app", "/root")

        fun screenshot(name: String, id: String) = fakeFunction(
            name = name,
            file = fakeFile("$name.kt", "/root/$name.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", id)),
                ),
            ),
        )

        // A second round sees functions generated by other processors in the first
        processor.process(fakeResolver(screenshots = listOf(screenshot("Home", "home"))))
        processor.process(fakeResolver(screenshots = listOf(screenshot("Detail", "detail"))))
        assertTrue(codeGenerator.createdFiles.isEmpty())
        processor.finish()

        assertEquals(1, codeGenerator.createdFiles.size)
        val manifest = StoreManifest.decodeFromString(
            codeGenerator.createdFiles.values.single().toString(Charsets.UTF_8),
        )
        assertEquals(listOf("detail", "home"), manifest.entries.map { it.id })
    }

    @Test
    fun recompileWithAnnotationRemovedDropsItFromManifest() {
        fun screenshot(name: String, id: String) = fakeFunction(
            name = name,
            file = fakeFile("$name.kt", "/root/$name.kt"),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", id)),
                ),
            ),
        )

        fun manifestIds(codeGenerator: FakeCodeGenerator): List<String> =
            StoreManifest.decodeFromString(
                codeGenerator.createdFiles.getValue("META-INF/store-studio/store-manifest.json")
                    .toString(Charsets.UTF_8),
            ).entries.map { it.id }

        // First compile: two annotated functions
        val first = FakeCodeGenerator()
        val firstProcessor = StoreStudioSymbolProcessor(first, FakeLogger(), ":app", "/root")
        firstProcessor.process(
            fakeResolver(
                screenshots = listOf(screenshot("Home", "home"), screenshot("Detail", "detail")),
            ),
        )
        firstProcessor.finish()
        assertEquals(listOf("detail", "home"), manifestIds(first))

        // Second compile: @StoreScreenshot removed from "Detail"
        val second = FakeCodeGenerator()
        val secondProcessor = StoreStudioSymbolProcessor(second, FakeLogger(), ":app", "/root")
        secondProcessor.process(fakeResolver(screenshots = listOf(screenshot("Home", "home"))))
        secondProcessor.finish()
        assertEquals(listOf("home"), manifestIds(second))
    }

    @Test
    fun rejectsDuplicateIdsInSameModule() {
        val codeGenerator = FakeCodeGenerator()
        val logger = FakeLogger()
        val processor = StoreStudioSymbolProcessor(codeGenerator, logger, ":app", "/root")

        val file1 = fakeFile("ScreenA.kt", "/root/ScreenA.kt")
        val file2 = fakeFile("ScreenB.kt", "/root/ScreenB.kt")

        val func1 = fakeFunction(
            name = "HeroA",
            file = file1,
            location = FileLocation("/root/ScreenA.kt", 10),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "duplicate_id")),
                ),
            ),
        )
        val func2 = fakeFunction(
            name = "HeroB",
            file = file2,
            location = FileLocation("/root/ScreenB.kt", 20),
            annotations = listOf(
                fakeAnnotation("Composable", "androidx.compose.runtime.Composable"),
                fakeAnnotation(
                    shortName = "StoreScreenshot",
                    qualifiedName = StoreScreenshot::class.qualifiedName!!,
                    arguments = listOf(fakeArgument("id", "duplicate_id")),
                ),
            ),
        )

        processor.process(fakeResolver(screenshots = listOf(func1, func2)))

        processor.finish()

        val hasDuplicateError = logger.errors.any {
            it.contains("Duplicate @StoreScreenshot id 'duplicate_id'")
        }
        assertTrue(hasDuplicateError)
    }

    @Test
    fun respectsJvmNameAnnotationOnFile() {
        val processor = StoreStudioSymbolProcessor(
            codeGenerator = FakeCodeGenerator(),
            logger = FakeLogger(),
            modulePath = ":app",
            projectRootDir = "/root",
        )

        val jvmNameAnnotation = fakeAnnotation(
            shortName = "JvmName",
            qualifiedName = "kotlin.jvm.JvmName",
            arguments = listOf(fakeArgument("name", "CustomFacade")),
        )
        val file = fakeFile(
            "OriginalName.kt",
            "/root/OriginalName.kt",
            annotations = listOf(jvmNameAnnotation),
        )
        val function = fakeFunction(
            name = "MyScreen",
            packageName = "com.store",
            file = file,
        )

        val fqn = processor.resolveJvmFqn(function)
        assertEquals("com.store.CustomFacade.MyScreen", fqn)
    }

    @Test
    fun facadeNameMatchesKotlinCompilerConventions() {
        val processor = StoreStudioSymbolProcessor(
            codeGenerator = FakeCodeGenerator(),
            logger = FakeLogger(),
            modulePath = ":app",
            projectRootDir = "/root",
        )

        fun fqnFor(fileName: String): String = processor.resolveJvmFqn(
            fakeFunction(
                name = "Shot",
                packageName = "com.store",
                file = fakeFile(fileName, "/root/$fileName"),
            ),
        )

        assertEquals("com.store.OnboardingScreenKt.Shot", fqnFor("OnboardingScreen.kt"))
        assertEquals("com.store.ScreensKt.Shot", fqnFor("screens.kt"))
        assertEquals("com.store.Screens_androidKt.Shot", fqnFor("Screens.android.kt"))
        assertEquals("com.store.My_screensKt.Shot", fqnFor("my-screens.kt"))
        assertEquals("com.store._1screensKt.Shot", fqnFor("1screens.kt"))
    }
}

// -----------------------------------------------------------------------------------------
// Test Fakes & Proxies
// -----------------------------------------------------------------------------------------

class FakeCodeGenerator : CodeGenerator {
    val createdFiles = mutableMapOf<String, ByteArrayOutputStream>()

    override val generatedFile: Collection<File> get() = emptyList()

    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
        val path = if (packageName.isEmpty()) {
            "$fileName.$extensionName"
        } else {
            "$packageName/$fileName.$extensionName"
        }
        val stream = ByteArrayOutputStream()
        createdFiles[path] = stream
        return stream
    }

    override fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String,
    ): OutputStream {
        val fullPath = if (extensionName.isEmpty()) path else "$path.$extensionName"
        val stream = ByteArrayOutputStream()
        createdFiles[fullPath] = stream
        return stream
    }

    override fun associate(
        sources: List<KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {}

    override fun associateByPath(sources: List<KSFile>, path: String, extensionName: String) {}

    override fun associateWithClasses(
        classes: List<KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) {}
}

class FakeLogger : KSPLogger {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()

    override fun error(message: String, symbol: KSNode?) {
        errors.add(message)
    }

    override fun warn(message: String, symbol: KSNode?) {
        warnings.add(message)
    }

    override fun info(message: String, symbol: KSNode?) {
        infos.add(message)
    }

    override fun logging(message: String, symbol: KSNode?) {}
    override fun exception(e: Throwable) {
        errors.add(e.message ?: "Exception")
    }
}

private inline fun <reified T : Any> createProxy(
    crossinline handler: (method: String, args: Array<Any?>) -> Any?,
): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { proxy, method, args ->
    when (method.name) {
        "hashCode" -> System.identityHashCode(proxy)
        "equals" -> args?.firstOrNull() === proxy
        else -> handler(method.name, args ?: emptyArray())
    }
} as T

private fun fakeName(name: String): KSName = createProxy { method, _ ->
    when (method) {
        "asString" -> name
        "getQualifier" -> name.substringBeforeLast('.', "")
        "getShortName" -> name.substringAfterLast('.')
        "toString" -> name
        else -> null
    }
}

private fun fakeFile(
    fileName: String,
    filePath: String,
    packageName: String = "com.example",
    annotations: List<KSAnnotation> = emptyList(),
): KSFile = createProxy { method, _ ->
    when (method) {
        "getFileName" -> fileName
        "getFilePath" -> filePath
        "getPackageName" -> fakeName(packageName)
        "getAnnotations" -> annotations.asSequence()
        "getLocation" -> FileLocation(filePath, 1)
        "toString" -> "KSFile($fileName)"
        else -> null
    }
}

private fun fakeArgument(name: String?, value: Any?): KSValueArgument = createProxy { method, _ ->
    when (method) {
        "getName" -> name?.let { fakeName(it) }
        "getValue" -> value
        "toString" -> "Arg($name=$value)"
        else -> null
    }
}

private fun fakeAnnotation(
    shortName: String,
    qualifiedName: String,
    arguments: List<KSValueArgument> = emptyList(),
): KSAnnotation {
    val decl: KSClassDeclaration = createProxy { method, _ ->
        when (method) {
            "getSimpleName" -> fakeName(shortName)
            "getQualifiedName" -> fakeName(qualifiedName)
            else -> null
        }
    }

    val type: KSType = createProxy { method, _ ->
        when (method) {
            "getDeclaration" -> decl
            else -> null
        }
    }

    val typeRef: KSTypeReference = createProxy { method, _ ->
        when (method) {
            "resolve" -> type
            else -> null
        }
    }

    return createProxy { method, _ ->
        when (method) {
            "getShortName" -> fakeName(shortName)
            "getAnnotationType" -> typeRef
            "getArguments" -> arguments
            "toString" -> "@$shortName"
            else -> null
        }
    }
}

private fun fakeParam(name: String): KSValueParameter = createProxy { method, _ ->
    when (method) {
        "getName" -> fakeName(name)
        else -> null
    }
}

private fun fakeClass(name: String): KSClassDeclaration = createProxy { method, _ ->
    when (method) {
        "getSimpleName" -> fakeName(name)
        "getQualifiedName" -> fakeName(name)
        else -> null
    }
}

private fun fakeFunction(
    name: String,
    packageName: String = "com.example",
    file: KSFile? = null,
    parent: KSDeclaration? = null,
    params: List<KSValueParameter> = emptyList(),
    modifiers: Set<Modifier> = setOf(Modifier.PUBLIC),
    annotations: List<KSAnnotation> = emptyList(),
    location: Location = NonExistLocation,
): KSFunctionDeclaration = createProxy { method, _ ->
    when (method) {
        "getSimpleName" -> fakeName(name)
        "getPackageName" -> fakeName(packageName)
        "getQualifiedName" -> fakeName("$packageName.$name")
        "getContainingFile" -> file
        "getParentDeclaration" -> parent
        "getParameters" -> params
        "getModifiers" -> modifiers
        "getAnnotations" -> annotations.asSequence()
        "getLocation" -> location
        "toString" -> "Function($packageName.$name)"
        else -> null
    }
}

private fun fakeResolver(
    screenshots: List<KSAnnotated> = emptyList(),
    features: List<KSAnnotated> = emptyList(),
): Resolver = createProxy { method, args ->
    when (method) {
        "getSymbolsWithAnnotation" -> {
            val name = args.firstOrNull() as? String
            when (name) {
                StoreScreenshot::class.qualifiedName -> screenshots.asSequence()
                StoreFeatureGraphic::class.qualifiedName -> features.asSequence()
                else -> emptySequence<KSAnnotated>()
            }
        }

        else -> null
    }
}
