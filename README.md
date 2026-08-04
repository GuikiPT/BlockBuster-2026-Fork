# Blockbuster (Fabric 1.20.4 full port)

**`blockbuster-3.0.0-port`** — a single Fabric 1.20.4 / Java 17 mod that is a
**full port** of Blockbuster 2.7.2 (Forge 1.12.2). Design, direct and act in
your own Minecraft machinimas: record actor takes, replay them as a director
scene, morph into custom box/OBJ/VOX models or animated Blockbench models, and
drive an Aperture-style camera over the whole thing.

The needed functionality of Blockbuster's original dependencies — **McLib
2.4.3**, **Metamorph 1.4** and **Aperture 1.8.2** — the Metamorph add-on
**Chameleon 1.2.2**, and the vendored `open_imaging` GIF decoder are
**reimplemented and bundled inside this one jar**. There are no separate mod
downloads.

## The behavior bar: 1.12.2 parity

This port's design contract is that it **behaves exactly like Blockbuster
2.7.2 on Forge 1.12.2** — same features, same file formats, same folder layout,
same GUI flow, same command syntax. Old data loads 1:1:

- `.dat` recordings (gzip-NBT, `SIGNATURE 148`), scene/director NBT, `model.json`
  box-limb models, OBJ/VOX models, Metamorph morph NBT, Aperture camera profiles
  and mclib module configs all parse byte-for-byte as they did in 2.7.2.
- Legacy quirks are preserved on purpose (e.g. `Frame` writes `motionX` into all
  three `MX/MY/MZ` keys); readers never "fix" them.
- Every legacy-file reader is **total**: unknown/unsupported input produces a
  logged warning plus a placeholder, never a crash. Pre-flattening block/item/
  entity ids are translated through a central migration shim.

The one intentional version split: the in-code format marker
`Blockbuster.VERSION` stays `"2.7.2"` so version-embedding outputs (e.g. the
OBJ/MTL export header) remain byte-identical to files produced by 2.7.2, while
the **mod distribution version is `3.0.0-port`** (this is what the jar filename
and the client/server handshake report).

## Install

1. Install **Fabric Loader ≥ 0.15.0** for **Minecraft 1.20.4**.
2. Install **Fabric API** (`fabric-api`, a hard dependency).
3. Drop **`blockbuster-3.0.0-port.jar`** into your `mods/` folder.

Java 17+ is required. The mod runs on both sides (`"environment": "*"`) — the
recording/scene engine lives server-side, the editors and rendering client-side
— so on a dedicated server, install it server-side as well.

> Integrator note: the release jar is now named **`blockbuster-3.0.0-port.jar`**.
> Earlier dev builds produced `blockbuster-2.7.2-1.20.4.jar`; the P216 release
> freeze drops the `-1.20.4` suffix. Any deploy/copy script that hardcodes the
> old filename should match `blockbuster-*.jar` instead.

## Folder layout (identical to 1.12.2)

The port keeps 2.7.2's exact on-disk folders, so an old install's data drops in
unchanged:

```
config/blockbuster/
├── models/            custom models (model.json / OBJ / VOX), per-model skins/
│   └── particles/     Bedrock particle scheme .json files
├── audio/             scene audio
└── skins/             player-supplied skins

<world>/blockbuster/
├── records/           .dat recordings
├── scenes/            .dat director scenes
└── models/            world-local models

config/chameleon/
└── models/            Chameleon models, one folder each:
                       <name>.geo.json + <name>.animation.json
                       (or animations/*.animation.json) + skins/
```

A Chameleon model folder becomes the morph `chameleon.<folder>`; nesting is
allowed, so `models/packs/wolf` is `chameleon.packs/wolf` and shows up in the
creative picker under its own sub-category.

Bundled defaults ship inside the jar and are used directly or extracted on
first run, matching 2.7.2:

- the **32 default `models/entity` assets** (steve/alex/fred + `_3d` variants,
  cape, empty, and the `eyes/*` + `mchorse/head` set),
- the **4 default Bedrock particle schemes** (`default_fire`, `default_magic`,
  `default_rain`, `default_snow`),
- all **7 Blockbuster language files** (`en_us`, `fr_fr`, `pt_br`, `ru_ru`,
  `uk_ua`, `zh_cn`, `zh_tw`),
- the **default skin** bootstrap (`textures/gui/icon.png` copied to
  `models/image/skins/default.png` on first run).

Like 2.7.2, the mod ships **zero** default `.dat` records — the
`/assets/blockbuster/records/<name>.dat` serving path exists for jar-embedded
records but is empty in the stock distribution.

### Legacy world migration

Old worlds that still contain pre-scene **director blocks** have their embedded
director data converted to `<world>/blockbuster/scenes/director_block_<x>_<y>_<z>.dat`
on load (the migration is silent on save failure, exactly as 2.7.2 was). Pre-1.13
block/item/entity ids embedded in recordings, morphs and structure palettes are
flattened through the migration shim on read.

## Known deviations from 2.7.2

Deliberate departures: a handful of GUI-framework details (including a legacy
NPE that is fixed rather than reproduced), chroma icon sharing, and some camera
/ `.vox` gaps. Everything else is held to byte / behavior parity.

**Out of scope (post-parity backlog, S21):** Iris/Sodium render-compat,
shader-bound Aperture curves, Emoticons interop, and 1.20.1/NeoForge
multi-loader support are explicitly *not* part of the 1.12.2 parity target.

## License

The combined work ships under **GPL-3.0-only** (full text in [`LICENSE`](LICENSE)).
Bundled components carry their own terms — Blockbuster & Aperture (GPL-3.0),
McLib, Metamorph & Chameleon (MIT), and the `open_imaging` GifDecoder
(Apache-2.0) — all
recorded verbatim in [`ATTRIBUTION.md`](ATTRIBUTION.md). Original mods and
libraries are by **McHorse**.

## Development

Build with the pinned toolchain wrapper (Temurin 17, Gradle 8.6 / Loom 1.6):

```bash
./gradlew build
```

This produces the remapped jar under `build/libs/`. If a MultiMC 1.20.4
instance exists at `~/.local/share/multimc/instances/1.20.4`, `build` also
drops the jar into its `mods/` folder so in-game testing needs no manual copy.
