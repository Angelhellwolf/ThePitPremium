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
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
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
    // Java 15+ removed Nashorn from the JDK; bundle nashorn-core so JSHandler works on Java 17+ runtimes.
    implementation(libs.narshorn)
    compileOnly(libs.httpclient)
    compileOnly(libs.httpcore)
    compileOnly(libs.decentholograms)

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
    // Keep Nashorn package unchanged: Nashorn contains internal hardcoded class names loaded reflectively.
    relocate("org.objectweb.asm", "cn.charlotte.pit.shadow.asm")

}
// Prevent accidentally uploading the thin (non-shaded) jar: it misses bundled runtime deps like Nashorn for Java 17.
tasks.jar {
    enabled = false
}
tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}
kotlin {
    jvmToolchain(11)
}
