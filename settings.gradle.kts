pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "SaltLyrics"

// Add mirrors before Loom applies and adds its own repositories. This also makes
// detached remapping configurations use the same mirror as ordinary dependencies.
gradle.beforeProject {
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        mavenLocal()
    }
}
