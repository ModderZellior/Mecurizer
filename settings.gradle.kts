rootProject.name = "mercurizer"

pluginManagement {
    repositories {
        mavenLocal()
        maven { url = uri("https://maven.fabricmc.net/") }
        gradlePluginPortal()
    }
}

include("common")
include("frapi")
include("fabric")
