plugins {
    id("multiloader-base")
    id("maven-publish")
}

tasks {
    processResources {
        inputs.property("version", version)

        filesMatching(listOf("fabric.mod.json")) {
            expand(mapOf("version" to inputs.properties["version"]))
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.FAIL
        from(rootDir.resolve("LICENSE.md"))
    }
}

publishing {
    repositories {
        val isReleaseBuild = project.hasProperty("build.release")
        val mercurizerMavenUsername: String? by project
        val mercurizerMavenPassword: String? by project

        maven {
            name = "Mercurizer"
            url = uri("https://maven.caffeinemc.net".let {
                if (isReleaseBuild) "$it/releases" else "$it/snapshots"
            })

            credentials {
                username = mercurizerMavenUsername
                password = mercurizerMavenPassword
            }
        }
    }
}
