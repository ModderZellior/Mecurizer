import org.gradle.api.Project

object BuildConfig {
    val MINECRAFT_VERSION: String = "1.21.4"
    val SODIUM_VERSION: String = "0.6.13"
    val NEOFORGE_VERSION: String = "21.11.42"
    val FABRIC_LOADER_VERSION: String = "0.19.2"
    val FABRIC_API_VERSION: String = "0.119.4+1.21.4"
    val SUPPORT_FRAPI : Boolean = true

    val PARCHMENT_VERSION: String? = null

    val MOD_VERSION: String = "1.0.1"

    val RELEASE_TAG: String = "mc$MINECRAFT_VERSION-$MOD_VERSION"

    val CURSEFORGE_PROJECT_ID = "394468"
    val MODRINTH_PROJECT_ID = "AANobbMI"

    fun createVersionString(project: Project): String {
        val isReleaseBuild = project.hasProperty("build.release")
        val buildId = System.getenv("GITHUB_RUN_NUMBER")

        val base = "$SODIUM_VERSION+mc$MINECRAFT_VERSION-$MOD_VERSION"

        return if (buildId != null) {
            "$base-build.$buildId"
        } else {
            base
        }
    }

    fun calculateGitHash(project: Project): String = try {
        val output = project.providers.exec {
            workingDir(project.projectDir)
            commandLine("git", "rev-parse", "HEAD")
        }
        output.standardOutput.asText.get().trim()
    } catch (_: Throwable) {
        "unknown"
    }

    fun getChangelog(project: Project): String = project.rootProject.file("CHANGELOG.md").readText()
            .split("----------")[1]
            .trim()
            .replace("[ReleaseTag]()", RELEASE_TAG)
            .replace("[MCVersion]()", MINECRAFT_VERSION)
            .replace("[SodiumVersion]()", SODIUM_VERSION)
}
