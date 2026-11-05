import org.gradle.api.tasks.compile.JavaCompile

plugins { id("com.android.library") }

val humla = rootProject.file("third_party/humla")
val preparedSources = layout.buildDirectory.dir("generated/humla")
val prepareSources = tasks.register<Sync>("prepareHumlaSources") {
    inputs.file("patches/pinned-trust.patch")
    from(humla.resolve("src/main/java"))
    into(preparedSources)
    doLast {
        val patch = file("patches/pinned-trust.patch")
        val result = ProcessBuilder("git", "apply", "--unsafe-paths", patch.absolutePath)
            .directory(preparedSources.get().asFile).inheritIO().start().waitFor()
        check(result == 0) { "Humla pinning patch did not apply" }
    }
}
val buildSpongycastle = tasks.register<Exec>("buildSpongycastle") {
    workingDir(humla.resolve("libs/humla-spongycastle"))
    commandLine("../../gradlew", "jar", "--no-daemon", "--quiet")
}
val buildNative = tasks.register<Exec>("buildHumlaNative") {
    val ndk = file("${System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")}/ndk/25.1.8937393/ndk-build")
    commandLine(ndk.absolutePath, "-C", humla.resolve("src/main/jni").absolutePath)
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
        jniLibs.srcDir(humla.resolve("src/main/libs"))
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
