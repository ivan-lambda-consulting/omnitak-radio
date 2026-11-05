import org.gradle.api.tasks.compile.JavaCompile

plugins { id("com.android.library") }

val humla = rootProject.file("third_party/humla")
val preparedSources = layout.buildDirectory.dir("generated/humla")
val nativeSources = layout.buildDirectory.dir("generated/native-src")
val nativeLibraries = layout.buildDirectory.dir("generated/native-libs")
val nativeObjects = layout.buildDirectory.dir("generated/native-obj")
val prepareSources = tasks.register<Sync>("prepareHumlaSources") {
    inputs.file("patches/pinned-trust.patch")
    inputs.property("patchVersion", 1)
    from(humla.resolve("src/main/java"))
    into(preparedSources)
    doLast {
        val patch = file("patches/pinned-trust.patch")
        val result = ProcessBuilder("patch", "--batch", "--forward", "-p1", "-d",
            preparedSources.get().asFile.absolutePath, "-i", patch.absolutePath)
            .inheritIO().start().waitFor()
        check(result == 0) { "Humla pinning patch did not apply" }
        val trustSource = preparedSources.get().file("se/lublin/humla/net/HumlaSSLSocketFactory.java")
            .asFile.readText()
        check("Voice server pin required" in trustSource &&
            "mDefaultTrustManager.checkServerTrusted" !in trustSource) { "Voice TLS remains unpinned" }
    }
}
val buildSpongycastle = tasks.register<Exec>("buildSpongycastle") {
    workingDir(humla.resolve("libs/humla-spongycastle"))
    commandLine("../../gradlew", "jar", "--no-daemon", "--quiet")
}
val prepareNativeSources = tasks.register<Sync>("prepareNativeSources") {
    inputs.file("patches/opus-cve-2017-0381.patch")
    inputs.property("patchVersion", 1)
    from(humla.resolve("src/main/jni")) { exclude("**/.git", "**/.git/**") }
    into(nativeSources)
    doLast {
        val result = ProcessBuilder("patch", "--batch", "--forward", "-p1", "-d",
            nativeSources.get().asFile.absolutePath, "-i",
            file("patches/opus-cve-2017-0381.patch").absolutePath)
            .inheritIO().start().waitFor()
        check(result == 0) { "Opus CVE-2017-0381 patch did not apply" }
        val opusSource = nativeSources.get().file("opus/silk/NLSF_stabilize.c").asFile.readText()
        check("silk_ADD_SAT16( NLSF_Q15[i-1]" in opusSource &&
            "silk_max_int( NLSF_Q15[i], NLSF_Q15[i-1] + NDeltaMin_Q15[i] )" !in opusSource) {
            "Unpatched Opus source would be compiled"
        }
    }
}
val buildNative = tasks.register<Exec>("buildHumlaNative") {
    dependsOn(prepareNativeSources)
    inputs.dir(nativeSources)
    outputs.dir(nativeLibraries)
    val ndk = file("${System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")}/ndk/25.1.8937393/ndk-build")
    commandLine(ndk.absolutePath, "-C", nativeSources.get().asFile.absolutePath,
        "NDK_PROJECT_PATH=null",
        "APP_BUILD_SCRIPT=${nativeSources.get().asFile.resolve("Android.mk").absolutePath}",
        "NDK_APPLICATION_MK=${nativeSources.get().asFile.resolve("Application.mk").absolutePath}",
        "NDK_LIBS_OUT=${nativeLibraries.get().asFile.absolutePath}",
        "NDK_OUT=${nativeObjects.get().asFile.absolutePath}")
}

android {
    namespace = "se.lublin.humla"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets.getByName("main").apply {
        java.srcDir(prepareSources)
        res.srcDir(humla.resolve("src/main/res"))
        jniLibs.srcDir(nativeLibraries)
    }
    buildFeatures { buildConfig = true }
}

tasks.withType<JavaCompile>().configureEach { dependsOn(buildNative, prepareSources) }
tasks.matching { it.name.startsWith("extract") && it.name.endsWith("Annotations") }
    .configureEach { dependsOn(prepareSources) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(buildNative) }
dependencies {
    api("com.google.protobuf:protobuf-java:3.11.4")
    api("com.madgag.spongycastle:core:1.51.0.0")
    api(files(humla.resolve("libs/humla-spongycastle/prov/build/libs/prov-1.51.0.0.jar"),
        humla.resolve("libs/humla-spongycastle/pkix/build/libs/pkix-1.51.0.0.jar"))
        .builtBy(buildSpongycastle))
    implementation("com.googlecode.javacpp:javacpp:0.7")
    implementation("org.jetbrains:annotations:18.0.0")
    implementation("org.minidns:minidns-hla:0.3.4")
    implementation("org.minidns:minidns-android21:0.3.4")
    implementation("com.google.guava:guava:28.2-android")
}
