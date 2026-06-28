[ReleaseTag]() is automatically replaced with the release tag.
[MCVersion]() is automatically replaced with the minecraft version.
Everything above the line is ignored. Everything below will be in the changelog on GitHub, Modrinth and CurseForge.

----------

## Mercurizer [ReleaseTag]() for Minecraft [MCVersion]()

- Adaptive tuning: after 30 seconds of stable gameplay, Mercurizer refines its upload budget based on real frame times instead of only the synthetic benchmark
- Fixed upload scaling being permanently suppressed on slow hardware (dynamic target now tracks the hardware's own best frame time instead of assuming 60 FPS)
- GPU and driver capabilities are now re-probed when re-running the benchmark
- Removed the settings screen rename that was hiding the Sodium donate button
- Fixed a silent error when benchmark.json could not be read
