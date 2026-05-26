plugins {
    id("multiloader-platform")
    id("net.fabricmc.fabric-loom") version ("1.16.1")
}

base {
    archivesName = "mercurizer-fabric"
}

val configurationCommonModJava: Configuration = configurations.create("commonJava") {
    isCanBeResolved = true
}

val configurationCommonModResources: Configuration = configurations.create("commonResources") {
    isCanBeResolved = true
}

dependencies {
    configurationCommonModJava(project(path = ":common", configuration = "commonMainJava"))
    configurationCommonModResources(project(path = ":common", configuration = "commonMainResources"))
}

sourceSets.apply {
    main {
        compileClasspath += configurationCommonModJava
        runtimeClasspath += configurationCommonModJava
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${BuildConfig.MINECRAFT_VERSION}")
    implementation("net.fabricmc:fabric-loader:${BuildConfig.FABRIC_LOADER_VERSION}")

    val sodiumJar = rootProject.file("libs/sodium-fabric-0.8.12+mc${BuildConfig.MINECRAFT_VERSION}.jar")
    compileOnly(files(sodiumJar))
}

loom {
    accessWidenerPath.set(file("src/main/resources/mercurizer-fabric.accesswidener"))

    mixin {
        useLegacyMixinAp = false
    }

    runs {
        named("client") {
            client()
            configName = "Fabric/Client"
            appendProjectPathToConfigName = false
            ideConfigGenerated(true)
            runDir("run")
        }
    }
}

tasks {
    jar {
        from(configurationCommonModJava)
        destinationDirectory.set(file(rootProject.layout.buildDirectory).resolve("mods"))
    }

    processResources {
        from(configurationCommonModResources)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group as String
            artifactId = rootProject.name + "-" + project.name
            version = version
            from(components["java"])
        }
    }
}
