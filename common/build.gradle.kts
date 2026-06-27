plugins {
    id("multiloader-base")
    id("java-library")
    id("net.fabricmc.fabric-loom") version ("1.16.1")
}

base {
    archivesName = "mercurizer-common"
}

repositories {
    mavenLocal()
    maven { url = uri("https://api.modrinth.com/maven") }
}

dependencies {
    minecraft("com.mojang:minecraft:${BuildConfig.MINECRAFT_VERSION}")

    compileOnly("io.github.llamalad7:mixinextras-common:0.5.0")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.0")

    compileOnly("net.fabricmc:sponge-mixin:0.13.2+mixin.0.8.5")
    compileOnly("net.fabricmc:fabric-loader:${BuildConfig.FABRIC_LOADER_VERSION}")

    compileOnly(files(rootProject.file("libs/sodium-fabric-0.8.9+mc26.1.1.jar")))
}

loom {
    accessWidenerPath = file("src/main/resources/mercurizer-common.accesswidener")

    mixin {
        useLegacyMixinAp = false
    }
}

fun exportSourceSetJava(name: String, sourceSet: SourceSet) {
    val configuration = configurations.create("${name}Java") {
        isCanBeResolved = true
        isCanBeConsumed = true
    }
    val compileTask = tasks.getByName<JavaCompile>(sourceSet.compileJavaTaskName)
    artifacts.add(configuration.name, compileTask.destinationDirectory) {
        builtBy(compileTask)
    }
}

fun exportSourceSetResources(name: String, sourceSet: SourceSet) {
    val configuration = configurations.create("${name}Resources") {
        isCanBeResolved = true
        isCanBeConsumed = true
    }
    val compileTask = tasks.getByName<ProcessResources>(sourceSet.processResourcesTaskName)
    artifacts.add(configuration.name, compileTask.destinationDir) {
        builtBy(compileTask)
    }
}

exportSourceSetJava("commonMain", sourceSets["main"])
exportSourceSetResources("commonMain", sourceSets["main"])

tasks.jar { enabled = false }
