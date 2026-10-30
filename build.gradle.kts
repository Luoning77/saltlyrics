import java.util.Properties
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

plugins {
    id("dev.architectury.loom") version "1.13-SNAPSHOT"
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
}
version = Properties().apply { file("version.properties").inputStream().use { load(it) } }.getProperty("version")
group = "com.luoning.saltlyrics"
base { archivesName.set("SaltLyrics") }
repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/releases/") }
        filter { includeGroup("com.terraformersmc") }
    }
}
val shade by configurations.creating
configurations.implementation { extendsFrom(shade) }
dependencies {
    minecraft("com.mojang:minecraft:1.20.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.10")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.92.2+1.20.1")
    // Optional integration: compile against the API without bundling or requiring Mod Menu.
    modCompileOnly("com.terraformersmc:modmenu:7.2.2") { isTransitive = false }
    shade(kotlin("stdlib"))
    testImplementation("junit:junit:4.13.2")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}
tasks.jar { from("LICENSE", "THIRD_PARTY_NOTICES.md") }
tasks.named<Jar>("sourcesJar") { from("LICENSE", "THIRD_PARTY_NOTICES.md") }
val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shade)
    from("LICENSE", "THIRD_PARTY_NOTICES.md")
    archiveClassifier.set("dev-shadow")
    relocate("kotlin", "com.luoning.saltlyrics.shadow.kotlin")
    exclude("module-info.class", "META-INF/versions/**", "META-INF/*.kotlin_module")
    addMultiReleaseAttribute.set(false)
}
tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(shadowJar.flatMap { it.archiveFile })
    archiveVersion.set("${project.version}+1.20.1")
    archiveClassifier.set("fabric")
}

// These are Loom's game launcher helpers, not needed by the protocol/model unit tests.
configurations.named("testRuntimeClasspath") {
    exclude(group = "net.fabricmc", module = "dev-launch-injector")
    exclude(group = "net.fabricmc", module = "fabric-log4j-util")
}
tasks.register<Zip>("sourceArchive") {
    archiveBaseName.set("SaltLyrics")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("source-project")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(projectDir) {
        exclude(".gradle/**", ".kotlin/**", "build/**", "run/**", ".git/**", ".idea/**", "prompt.md", ".DS_Store")
    }
}
