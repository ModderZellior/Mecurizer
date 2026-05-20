<img src="common/src/main/resources/mercurizer-icon.png" width="128">

# Mercurizer

Mercurizer is a Fabric rendering fork derived from Sodium and tuned for low-end PCs, integrated graphics, lower memory pressure, smoother frame times, and safer laptop behavior.

## Credits

- Mercurizer authors: Zellior and pxdritz ([Edonme](https://edonme.dev/))
- Original Sodium author and upstream foundation: JellySquid

## Project Structure

This repository uses a standard Gradle multi-module layout:

- `common/` shared code and assets
- `fabric/` Fabric-specific entrypoints and packaging
- `frapi/` FRAPI integration
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

This project is licensed under the GNU Lesser General Public License v3.0. See [LICENSE.md](LICENSE.md).
