# Mercurizer Porting Notes

This document explains what Mercurizer changes relative to upstream Sodium and what must be preserved when porting Mercurizer changes to other Sodium-based branches such as `1.21.11`, `26.1`, and `26.1.1`.

It is written as a handoff for another development session or for manual porting work.

## Scope

This note is based on a full repo comparison between this repository and:

- `sodium-26.1.2-stable.zip`
- `sodium-dev.zip`

Full line-by-line diff reports are included in this repository:

- `mercurizer-vs-sodium-stable-full-diff.txt`
- `mercurizer-vs-sodium-dev-full-diff.txt`

## Short Answer

Mercurizer is not just a rename of Sodium.

The meaningful differences are:

- Mercurizer is Fabric-only and removes NeoForge support.
- Mercurizer adds a runtime safety policy that clamps aggressive rendering behavior on problematic hardware and workaround states.
- Mercurizer rewires Sodium's chunk upload, staging, and worker scheduling around safer defaults.
- Mercurizer replaces Sodium-specific mixin/bootstrap/resource identifiers with Mercurizer-specific ones.
- Mercurizer adds Mercurizer-specific resource-pack metadata parsing.
- Mercurizer removes Sodium's donation/community UI surface.
- Relative to `sodium-dev`, Mercurizer also diverges in chunk scheduling/culling architecture and is not a straight fast-forward of dev.

## What Differs From Sodium

### 1. Branding, IDs, packaging, publishing

Mercurizer changes Sodium's project identity in build scripts, metadata, assets, and release targets.

Typical changes:

- project/release name changed from `Sodium` to `Mercurizer`
- GitHub release repo changed from `CaffeineMC/sodium` to `edonmc/mecurizer`
- display strings changed in UI/resources
- mixin/access widener/resource filenames renamed from `sodium-*` to `mercurizer-*`
- icon/resource namespace additions under `assets/mercurizer`

Files involved:

- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `README.md`
- `LICENSE.md`
- `common/src/main/resources/assets/sodium/lang/en_us.json`
- `fabric/src/main/resources/fabric.mod.json`
- `common/src/main/resources/mercurizer-common.mixins.json`
- `fabric/src/main/resources/mercurizer-fabric.mixins.json`
- `frapi/src/main/resources/mercurizer-frapi.mixins.json`

### 2. Fabric-only fork

Mercurizer removes NeoForge support that exists in upstream Sodium.

Upstream-only areas removed:

- `neoforge/` module
- NeoForge service wiring
- NeoForge mixins
- NeoForge platform abstractions
- NeoForge publication target in release workflow/build configuration

Practical meaning:

- When porting Mercurizer to another branch, do not try to preserve NeoForge paths unless that target branch explicitly wants them back.
- If the target repo is Fabric-only, this removal is intentional, not accidental.

### 3. Runtime safety policy

This is the main behavioral difference.

Mercurizer adds:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/MercurizerRuntimePolicy.java`

This class centralizes hardware/workaround-sensitive throttling logic.

Current policy behavior:

- prefers a safer staging path on Intel Gen8-or-older GPUs
- prefers a safer staging path when `AMD_GAME_OPTIMIZATION_BROKEN` workaround is active
- clamps CPU render-ahead
- disables no-error GL context in unsafe cases
- reduces chunk worker count
- reduces upload budget fraction
- reduces number of upload results processed per frame
- skips visible texture animation every other frame in safe mode

Current implementation details:

- default upload fraction: `0.04f`
- safe upload fraction: `0.025f`
- default max upload results per frame: `12`
- safe max upload results per frame: `6`
- safe chunk worker cap: `1`
- non-safe chunk worker cap: `2`

If another branch loses this file or stops calling into it, it is no longer Mercurizer in behavior.

### 4. Safer staging/upload path

Mercurizer threads the runtime policy into the buffer upload path.

Files involved:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/gl/buffer/GlBufferStreamer.java`

Behavioral change:

- upstream Sodium uses the faster mapped staging path whenever supported
- Mercurizer explicitly avoids that path when `MercurizerRuntimePolicy.preferSafeStagingPath()` is true

Porting rule:

- preserve this decision point even if the exact buffer API changes between Sodium versions
- the important part is the policy gate, not the exact method layout

### 5. Chunk worker and upload throttling

Mercurizer modifies Sodium's chunk system behavior to be more conservative.

Files involved:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/mixin/core/MinecraftMixin.java`

Observed differences relative to stable Sodium:

- chunk worker count is routed through `MercurizerRuntimePolicy.getEffectiveChunkWorkerCount(...)`
- upload duration budget floor changed from `2_000_000L` to `1_000_000L`
- frame upload fraction now comes from `MercurizerRuntimePolicy.getUploadFraction()`
- number of build results uploaded each frame is capped by `MercurizerRuntimePolicy.getMaxUploadResultsPerFrame()`
- visible texture animation can be skipped on alternating frames in safe mode
- CPU render-ahead is clamped via `MercurizerRuntimePolicy.getEffectiveCpuRenderAheadLimit(...)`
- translucent sort behavior is changed in safe mode

This is the core "Mercurizer feel":

- less aggressive work submission
- lower upload pressure
- lower likelihood of GPU/driver instability
- more stable frametimes on weak hardware

### 6. No-error context gating

Mercurizer changes how no-error GL context is enabled.

File involved:

- `common/src/main/java/net/caffeinemc/mods/sodium/mixin/core/WindowMixin.java`

Behavior:

- upstream Sodium enables no-error context if configured and not blocked by upstream workaround checks
- Mercurizer adds `MercurizerRuntimePolicy.shouldUseNoErrorContext(...)`

Porting rule:

- preserve the extra safety gate even if the target branch's GLFW/window code moved

### 7. Mercurizer mixin plugin and config naming

Mercurizer replaces Sodium's mixin bootstrap naming and config path.

Added:

- `common/src/main/java/net/caffeinemc/mods/sodium/mixin/MercurizerMixinPlugin.java`
- `common/src/main/resources/mercurizer-common.mixins.json`
- `fabric/src/main/resources/mercurizer-fabric.mixins.json`
- `frapi/src/main/resources/mercurizer-frapi.mixins.json`
- `common/src/main/resources/mercurizer-common.accesswidener`
- `fabric/src/main/resources/mercurizer-fabric.accesswidener`
- `frapi/src/main/resources/mercurizer-frapi.accesswidener`

Removed upstream counterparts:

- `SodiumMixinPlugin.java`
- `sodium-common.mixins.json`
- `sodium-fabric.mixins.json`
- `sodium-frapi.mixins.json`
- matching `sodium-*.accesswidener`

Behavioral detail:

- Mercurizer plugin reads `./config/mercurizer-mixins.properties`
- Mercurizer plugin disables all Mercurizer mixins if `embeddium` is detected in the loading list

Porting rule:

- if the target branch still uses Sodium naming, it must be converted consistently
- do not rename only some files; the plugin, JSON descriptors, access wideners, and resource references must stay in sync

### 8. Resource-pack metadata changes

Mercurizer replaces Sodium resource-pack metadata with Mercurizer metadata.

Added:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/checks/MercurizerResourcePackMetadata.java`

Removed:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/checks/SodiumResourcePackMetadata.java`

Updated:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/checks/ResourcePackScanner.java`

Meaning:

- Mercurizer reads a `mercurizer` metadata section from `pack.mcmeta`
- resource packs can declare Mercurizer shader exceptions via `ignored_shaders`

If this is missing after a port, resource-pack compatibility behavior differs from Mercurizer.

### 9. UI and community surface changes

Mercurizer removes upstream donation/community surface and adjusts strings/debug/UI content.

Removed upstream file:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/gui/widgets/DonationButtonWidget.java`

Changed files:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/gui/SodiumConfigBuilder.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/gui/SodiumDebugEntry.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/gui/VideoSettingsScreen.java`
- `common/src/main/resources/assets/sodium/lang/en_us.json`

These are lower priority than the runtime policy, but they are still part of the fork identity.

### 10. Additional namespace assets

Mercurizer adds its own namespace assets rather than only reusing Sodium ones.

Added:

- `common/src/main/resources/assets/mercurizer/lang/en_us.json`
- `common/src/main/resources/assets/mercurizer/shaders/...`
- `common/src/main/resources/assets/mercurizer/textures/gui/...`
- `common/src/main/resources/mercurizer-icon.png`

Removed upstream icon:

- `common/src/main/resources/sodium-icon.png`

Porting rule:

- keep Mercurizer assets aligned with metadata and UI references

## Extra Difference Relative to sodium-dev

Relative to `sodium-dev`, Mercurizer diverges more heavily in chunk scheduling/culling internals.

Mercurizer-only files in that area:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/TaskQueueType.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/OcclusionSectionCollector.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/TreeSectionCollector.java`

Upstream dev-only files not present here include several in:

- `render/chunk/async/`
- `render/chunk/lists/`
- `render/chunk/occlusion/`

Meaning:

- Mercurizer is not "Sodium dev with branding changes"
- if porting to a branch based on `sodium-dev`, chunk-system conflicts are expected
- port behavior, not line numbers

## Porting Priorities

When moving Mercurizer changes onto another Sodium version, preserve changes in this order.

### Priority 1: Required for Mercurizer behavior

- `MercurizerRuntimePolicy.java`
- all call sites that use the policy in renderer/upload/window/minecraft code
- Mercurizer mixin plugin and mixin/access widener naming
- Mercurizer resource-pack metadata parsing

If these are missing, the port is incomplete.

### Priority 2: Required for Mercurizer packaging/identity

- build metadata
- Fabric mod metadata
- release/publish configuration
- Mercurizer assets and icons

### Priority 3: Nice to keep aligned

- README/changelog/contributing/license text
- GitHub workflow/repo housekeeping
- debug text/UI cleanup

## Practical Porting Workflow For Other Branches

For each target branch:

1. Compare target branch against its matching upstream Sodium version.
2. Port Mercurizer identity files first:
   - build scripts
   - `fabric.mod.json`
   - mixin/access widener names
3. Port `MercurizerRuntimePolicy.java`.
4. Find the equivalent call sites in that branch for:
   - staging path selection
   - upload budget calculation
   - per-frame upload result draining
   - worker/thread count selection
   - CPU render-ahead clamping
   - no-error GL context setup
   - visible texture animation tick
   - translucent sort behavior
5. Port resource-pack metadata support.
6. Port UI/resource text changes.
7. Ignore NeoForge unless the target branch explicitly needs it.
8. Build and test on the target branch.

## Porting Heuristic

Do not chase exact line numbers between versions.

Instead, search for the behavior points:

- where staging buffer strategy is selected
- where chunk worker count is chosen
- where upload budget is computed per frame
- where build results are drained and uploaded
- where CPU render-ahead fences are limited
- where no-error GL context is requested
- where resource-pack metadata section types are declared
- where mixin plugin/config names are wired

If the method names changed upstream, preserve the Mercurizer decision logic at the new integration point.

## Known Oddities In This Repo

These should be checked before using this repo as a perfect source of truth:

- `.github/ISSUE_TEMPLATE/config.yml` appears to contain only `b`

Treat those as likely repo hygiene issues, not core behavioral design.

## Stable Comparison Summary

Compared with `sodium-26.1.2-stable`:

- `33` changed files
- `25` files only in Mercurizer
- `50` files only in upstream Sodium

Main Mercurizer-only source files:

- `common/src/main/java/net/caffeinemc/mods/sodium/client/MercurizerRuntimePolicy.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/client/checks/MercurizerResourcePackMetadata.java`
- `common/src/main/java/net/caffeinemc/mods/sodium/mixin/MercurizerMixinPlugin.java`
- `fabric/src/main/java/net/caffeinemc/mods/sodium/fabric/MercurizerFabricMod.java`
- `fabric/src/main/java/net/caffeinemc/mods/sodium/fabric/MercurizerPreLaunch.java`

## Dev Comparison Summary

Compared with `sodium-dev`:

- `77` changed files
- `28` files only in Mercurizer
- `64` files only in upstream Sodium dev

Main additional warning:

- the chunk/culling architecture differs enough that ports from dev-based branches will require judgment, not mechanical patching

## What The Next Maintainer Should Do

If Mercurizer is ported to a different Sodium version, the next maintainer should:

1. identify the matching upstream Sodium baseline for that branch
2. preserve Mercurizer's runtime safety policy semantics
3. preserve Mercurizer-specific mixin/resource naming
4. preserve Fabric-only packaging unless told otherwise
5. adapt Mercurizer changes to the target branch's chunk/render architecture instead of blindly replaying old diffs

## Minimal Definition Of A Correct Mercurizer Port

A target branch can be considered correctly ported if all of the following are true:

- Mercurizer-specific project/mod metadata is present
- Mercurizer mixin plugin and mixin configs are wired correctly
- Mercurizer resource-pack metadata support exists
- runtime safety policy exists and is actively used by renderer/upload/window code
- chunk upload/worker throttling reflects Mercurizer behavior
- the build is Fabric-focused and does not accidentally reintroduce removed upstream loader behavior
