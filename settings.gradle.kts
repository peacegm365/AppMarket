@file:Suppress("UnstableApiUsage")

rootProject.name = "AppMarket"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://developer.huawei.com/repo")
        maven("https://developer.hihonor.com/repo")
        // miuix-glass（玻璃弹层 / 玻璃导航栏）的 SNAPSHOT 只发布在 GitHub Packages，
        // 公共 Maven 上没有这个 artifact。没有 token 时跳过，不影响本地已有缓存。
        val ghPackagesToken = System.getenv("GITHUB_PACKAGES_TOKEN")
        if (!ghPackagesToken.isNullOrBlank()) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/compose-miuix-ui/miuix")
                credentials {
                    username = "token"
                    password = ghPackagesToken
                }
                content {
                    includeGroupAndSubgroups("top.yukonga.miuix.kmp")
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":domain")
include(":data")
include(":app:shared")
include(":app:android")
include(":app:desktop")
