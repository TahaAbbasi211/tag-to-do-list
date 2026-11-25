// Canonical repositories are used by default so CI and normal environments never hit
// third-party mirrors. Developers on throttled networks can opt into mirrors by setting
// `useMirrors=true` via `-PuseMirrors=true`, `ORG_GRADLE_PROJECT_useMirrors=true`, or
// `local.properties`.
pluginManagement {
    val useMirrors = providers.gradleProperty("useMirrors").orNull?.equals("true", ignoreCase = true)
        ?: java.io.File(settingsDir, "local.properties").takeIf { it.isFile }?.let { file ->
            java.util.Properties().apply { file.inputStream().use { load(it) } }
                .getProperty("useMirrors")?.equals("true", ignoreCase = true)
        } ?: false

    repositories {
        if (useMirrors) {
            maven {
                url = uri("https://maven.aliyun.com/repository/google")
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    if (useMirrors) {
        // Plugin marker artifacts are not mirrored, so resolve plugin ids straight to modules.
        resolutionStrategy {
            eachPlugin {
                when (requested.id.id) {
                    "com.android.application" ->
                        useModule("com.android.tools.build:gradle:${requested.version}")
                    "org.jetbrains.kotlin.android" ->
                        useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                    "org.jetbrains.kotlin.plugin.compose" ->
                        useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
                    "com.google.devtools.ksp" ->
                        useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
                }
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useMirrors = providers.gradleProperty("useMirrors").orNull?.equals("true", ignoreCase = true)
        ?: java.io.File(settingsDir, "local.properties").takeIf { it.isFile }?.let { file ->
            java.util.Properties().apply { file.inputStream().use { load(it) } }
                .getProperty("useMirrors")?.equals("true", ignoreCase = true)
        } ?: false

    repositories {
        if (useMirrors) {
            maven {
                url = uri("https://maven.aliyun.com/repository/google")
                content {
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("androidx.*")
                }
            }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Tag"
include(":app")
