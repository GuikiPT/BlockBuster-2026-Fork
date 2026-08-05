# BlockBuster 1.21.1 NeoForge migration

This branch is generated from the stabilized Fabric 1.21.1 migration pipeline and then converted into a native NeoForge workspace.

## Branch rules

- Fabric baseline: `gpt-1.21.1-full-port`
- NeoForge branch: `gpt-1.21.1-neoforge-port`
- Never merge this branch into the untouched `1.21.1` branch.

## Architecture

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.219
- Loom source migration from Yarn to official Mojang mappings
- Architectury Loom for the NeoForge workspace
- Forgified Fabric API for the already-ported public Fabric API calls
- Native `@Mod` entrypoint and `META-INF/neoforge.mods.toml`
- Access widener remapped into a NeoForge access transformer during `remapJar`
- Automated repairs for mapping-name collisions and unsupported compatibility callbacks

The source is still produced by the existing migration patch/scripts first. The workflow migrates the generated Fabric source to Mojmap, applies the NeoForge compatibility repairs, and then runs `.github/migrate_to_neoforge.py`. This keeps both branches aligned with the same gameplay fixes until a clean shared-source layout is committed.

## Build

Pushes to this branch run:

```text
Full Minecraft 1.21.1 NeoForge port build
```

The workflow compiles the NeoForge jar, packages rebuildable source, launches a headless NeoForge client smoke test, and uploads all outputs.
