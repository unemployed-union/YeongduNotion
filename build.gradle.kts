import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
}

group = "dev.notionfontnav"
version = "1.0.0"

val desktop = layout.projectDirectory.file(
    providers.gradleProperty("morpheDesktopJar").getOrElse(".local/tools/morphe-desktop.jar")
)
val sdk = providers.environmentVariable("ANDROID_HOME").orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
val toolsVersion = providers.gradleProperty("androidBuildTools").getOrElse("37.0.0")
val platform = providers.gradleProperty("androidPlatform").getOrElse("android-36")
val androidJar = sdk.map { File(it, "platforms/$platform/android.jar") }
val d8 = sdk.map { File(it, "build-tools/$toolsVersion/lib/d8.jar") }
val extension = sourceSets.create("extension")

dependencies {
    compileOnly(files(desktop))
    add(extension.compileOnlyConfigurationName, files(androidJar))
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileExtensionKotlin") {
    // Notion obfuscates its Kotlin runtime. Keep the injected code on Android and Java APIs.
    compilerOptions.freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
}
tasks.withType<JavaCompile>().configureEach { options.release.set(11) }

tasks.named("compileKotlin") {
    doFirst {
        require(desktop.asFile.isFile) { "Set -PmorpheDesktopJar to the Morphe Desktop 2.2.0 all-in-one JAR." }
    }
}

// Morphe loads the bundle's classes on the host and merges the extension only into the target app.
fun registerDexTask(taskName: String, jar: TaskProvider<Jar>, minApi: Int, needsPatcher: Boolean) =
    tasks.register<JavaExec>(taskName) {
        val archive = jar.flatMap { it.archiveFile }
        val dex = layout.buildDirectory.file("morphe/$taskName.zip")
        dependsOn(jar)
        inputs.files(archive, androidJar, d8)
        if (needsPatcher) inputs.file(desktop)
        outputs.file(dex)
        mainClass.set("com.android.tools.r8.D8")
        classpath(files(d8))
        doFirst {
            val output = dex.get().asFile
            output.parentFile.mkdirs()
            args("--release", "--min-api", minApi, "--lib", androidJar.get().absolutePath)
            if (needsPatcher) args("--classpath", desktop.asFile.absolutePath)
            args("--output", output.absolutePath, archive.get().asFile.absolutePath)
        }
    }

fun stageExtension(className: String, resourceName: String): TaskProvider<Copy> {
    val jar = tasks.register<Jar>("jar$className") {
        archiveClassifier.set(resourceName)
        from(extension.output)
        include("**/$className.class")
    }
    val dexName = "dex$className"
    val dex = registerDexTask(dexName, jar, 32, false)
    return tasks.register<Copy>("stage$className") {
        dependsOn(dex)
        from(layout.buildDirectory.file("morphe/$dexName.zip").map { zipTree(it) })
        include("classes.dex")
        rename { "$resourceName.dex" }
        into(layout.buildDirectory.dir("morphe/resources/$resourceName"))
    }
}

val bottomExtension = stageExtension("BottomSafeArea", "notion-bottom-extension")
val typographyExtension = stageExtension("Typography", "notion-typography-extension")
val dexPatches = registerDexTask("dexPatches", tasks.jar, 26, true)

tasks.register<Jar>("buildAndroid") {
    group = "build"
    description = "Builds notion-patches.mpp for Morphe Desktop and Manager."
    dependsOn(dexPatches)
    archiveFileName.set("notion-patches.mpp")
    from(sourceSets.main.get().output)
    from(bottomExtension, typographyExtension)
    from(layout.buildDirectory.file("morphe/dexPatches.zip").map { zipTree(it) })
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes(
        "Name" to "Notion Android UI",
        "Description" to "Typography and floating toolbar fixes for Notion on Samsung Galaxy.",
        "Version" to project.version.toString(),
        "Patcher-Version" to "2.4.0"
    )
}
