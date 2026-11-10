plugins {
    id("java")
    id("io.freefair.lombok") version "8.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm")
}

val pluginName = "ThePitPremium"
val version = "3.93"
repositories {
    maven("https://maven.aliyun.com/repository/public/")
    mavenCentral()

    maven("https://repo.crazycrew.us/releases")
    maven("https://repo.codemc.io/repository/nms/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.inventivetalent.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.rosewooddev.io/repository/public/")
    // Restrict PlaceholderAPI repo to avoid resolving unrelated deps from it (it can redirect/fail intermittently).
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        content {
            includeGroup("me.clip")
        }
    }
    maven("https://repo.panda-lang.org/releases")
    mavenLocal()
}

dependencies {
    // FAWE overrides part of the WorldEdit API this source targets. Put it first
    // so duplicate classes resolve to FAWE, while retaining WorldEdit-only classes.
    compileOnly(files("libs/fawe-1.0.jar"))
    compileOnly(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.jar"),
        "exclude" to listOf("fawe-1.0.jar")
    )))
    compileOnly("org.mongodb:mongo-java-driver:3.12.11")
    compileOnly("org.mongojack:mongojack:4.8.1")
    compileOnly("org.mongodb:mongodb-driver-sync:5.2.0")
    compileOnly("redis.clients:jedis:2.9.0")
    compileOnly("org.java-websocket:Java-WebSocket:1.6.0")
    compileOnly("it.unimi.dsi:fastutil:8.5.13")
    api(libs.slf4j)
    api(libs.book)
    api(libs.slf4j)
    api(libs.reflectionhelper)
    compileOnly(libs.luckperms)
    // Runtime diagnostics generate tiny compatibility classes and therefore need ASM in the shaded plugin.
    implementation(libs.asm)
    compileOnly(libs.httpclient)
    compileOnly(libs.httpcore)
    compileOnly(libs.decentholograms)

    val localPacketEvents = fileTree("libs").matching {
        include("packetevents-spigot-*.jar", "PacketEvents-*.jar")
    }.files.firstOrNull()
    if (localPacketEvents == null) {
        compileOnly(libs.packetevents)
    }

    // Network-stable fallback: if a matching ProtocolLib jar is present under libs/, use it and skip remote resolution.
    // Prefer any local ProtocolLib jar to avoid network/metadata issues and Java version variant mismatch.
    // Accepted filenames: libs/ProtocolLib-*.jar or libs/ProtocolLib*.jar
    val localProtocolLib = fileTree("libs").matching {
        include("ProtocolLib-*.jar", "ProtocolLib*.jar")
    }.files.firstOrNull()
    if (localProtocolLib != null) {
        compileOnly(files(localProtocolLib))
    } else {
        compileOnly(libs.protocollib)
    }

    compileOnly(libs.papi)
    compileOnly(libs.playerpoints)
    compileOnly(libs.spigot.get8())
}

tasks.shadowJar {
    archiveFileName.set("$pluginName-$version.jar")
    mergeServiceFiles()
    exclude("META-INF/**")
    // Isolate ASM to avoid Bukkit cross-plugin class collisions (same package loaded by different PluginClassLoader).
    relocate("org.objectweb.asm", "cn.charlotte.pit.shadow.asm")

}
// Prevent accidentally uploading the thin (non-shaded) jar: it misses bundled runtime dependencies.
tasks.jar {
    enabled = false
}
tasks.build {
    dependsOn(tasks.shadowJar)
}

// JS enchantments are retired for now. Keep their source files only as a rollback/migration
// reference; neither the engine bridge nor bundled scripts are compiled or packaged.
tasks.processResources {
    exclude("js/**")
}
sourceSets.named("main") {
    java.exclude("**/cn/charlotte/pit/js/**")
    java.exclude("**/cn/charlotte/pit/enchantment/type/JSEnchantment.kt")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/cn/charlotte/pit/js/**")
    exclude("**/cn/charlotte/pit/enchantment/type/JSEnchantment.kt")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
kotlin {
    jvmToolchain(11)
    sourceSets.named("main") {
        kotlin.exclude("**/cn/charlotte/pit/js/**")
        kotlin.exclude("**/cn/charlotte/pit/enchantment/type/JSEnchantment.kt")
    }
}
