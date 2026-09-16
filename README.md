<img src="common/src/main/resources/mercurizer-icon.png" width="128">

# Mercurizer

Mercurizer is a Sodium addon for Fabric tuned for low-end PCs, integrated graphics, lower memory pressure, smoother frame times, and safer laptop behavior.

## Credits

- Mercurizer authors: Zellior and pxdritz ([Edonme](https://edonme.dev/))

## Project Structure

This repository uses a standard Gradle multi-module layout:

- `common/` shared code and assets
- `fabric/` Fabric-specific entrypoints and packaging
- `buildSrc/` shared Gradle build logic
- `gradle/` Gradle wrapper files

Generated output, IDE files, and import snapshots are intentionally excluded from the repository structure.

## Building

Requirements:

- JDK 21

Build the project with:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

Built jars are written to `build/mods/`.

## License

Copyright © 2026 Zellior. All Rights Reserved.

You may not modify, redistribute, or resell this mod or its source code. See [LICENSE.md](LICENSE.md) for the full terms, including the modpack exception.
