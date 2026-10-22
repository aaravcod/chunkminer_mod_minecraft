# Chunk Miner v1.1.0

A Fabric mod for **Minecraft Java Edition 26.3**. Press **G** to mine the chunk containing your player. Press **G** again to cancel the active job.

## Portability

The compiled mod JAR is not tied to the PC or Minecraft installation where it was built. For another PC or another launcher instance running the same supported Minecraft version, copy the *same release JAR* into that instance's `mods` folder. You do not need to rebuild the mod on the other PC.

### Requirements

- Minecraft **Java Edition 26.3**
- Fabric Loader **0.19.5 or newer** compatible with Minecraft 26.3
- Fabric API version compatible with Minecraft 26.3
- Java **25** to run Minecraft 26.3 / this target setup
- For multiplayer, the mod must be installed on both the player's client and the server. Do not assume it will work on an unmodded server.

The mod is currently targeted at Minecraft 26.3; do not change the Minecraft version in `fabric.mod.json` just to force it to load on another version. A different Minecraft version may require source changes and a separate build/test.

## Install on another PC or launcher instance

1. Install Minecraft Java Edition 26.3 in the launcher you want to use.
2. Install Fabric Loader for Minecraft 26.3 in that launcher/instance.
3. Launch that Fabric profile once, then close Minecraft.
4. Open the **game directory for that specific profile/instance**. Do not assume every launcher uses the same `.minecraft` directory.
5. Create a folder named `mods` if it does not exist.
6. Copy `chunkminer-1.1.0.jar` and a compatible Fabric API JAR into that `mods` folder.
7. Start the Fabric profile and test in a disposable world first.

For a separate launcher installation, repeat these steps using that launcher's own game directory. Do not copy the source ZIP into `mods`; the `mods` folder needs the compiled `.jar` release.

## v1.1.0 behavior

- Scans chunk positions incrementally rather than creating a queue for every block up front.
- Uses `BlockState#getDestroyProgress` to accumulate vanilla mining progress, including block hardness, held-tool speed, Efficiency, and vanilla modifiers.
- Uses the server block-destruction pathway when progress completes.
- Stops when the held stack changes or the tool reaches the 10-durability reserve.
- Limits simultaneous mining jobs and ignores air, unbreakable blocks, bedrock, and blocks with block entities.
- Derives the target chunk from the server-side player position rather than trusting the client-supplied position.

## Build from source

Building is only necessary if you want to modify the source. To simply install the mod on another PC, use the same compiled release JAR.

Requires JDK 25 and a Gradle version compatible with the configured Fabric Loom version.

```bash
gradle build
```

The release JAR is generated under `build/libs/`.

## Before public distribution

Build locally and test in a disposable world. Verify dirt with a shovel, stone with a pickaxe, obsidian with a diamond/netherite pickaxe, Efficiency levels, Silk Touch/Fortune drops, tool durability reserve, cancellation, and multiplayer behavior. This source package has not been compiled in this environment; run the build locally and resolve any errors before distributing it.
