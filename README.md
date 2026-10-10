# Better Block Esp Addon - Autism Client Addon

Adds **per block colors** to AUTISM Client's built-in **BlockESP** module.

*Author: theflex5710*

This addon does **not** register a new module. It attaches three extra settings to the
existing `block-esp` module, so everything stays in one place.

## Features

- **Per-Block Colors** toggle — turn the feature on/off without touching other settings
- **Colored Blocks** list — pick exactly which blocks to scan and color (empty list = normal BlockESP)
- **One color picker per selected block** — a row appears for every block you add to the list
- Applies everywhere BlockESP draws: wireframe boxes, fill and tracers
- Block lookup uses the live world, so a highlighted block never falls back to the wrong color
- Cached chunk scans keep working (no extra scans, no FPS cost)

## New settings (BlockESP -> Colors)

| Setting | Default | What it does |
| --- | --- | --- |
| Per-Block Colors | off | Enables the whole feature |
| Colored Blocks | *(empty)* | The blocks to scan and color |
| `<Block name>` | red | Color for that block, only shown when the block is in the list |

## Usage

1. Open AUTISM Client settings -> **BlockESP**
2. Turn **Per-Block Colors** on
3. Add the blocks you want to **Colored Blocks**
4. A color picker appears for each of them - pick the color
5. Nothing else is drawn while the toggle is on; turn it off to fall back to normal
   BlockESP using the **Blocks** list and the main **Color** setting

## Installation

1. Install AUTISM Client (5.0-26.2) for Minecraft
2. Drop `Better Block Esp Addon-1.0.0.jar` into your `mods` folder
3. Launch the game

## Requirements

- Minecraft 26.2 with AUTISM Client `>= 5.0-26.2`
- Fabric Loader `>= 0.19.3`
- Fabric API
- Java 25+

## Build

```bash
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
.\gradlew.bat clean build
```

Output: `build/libs/Better Block Esp Addon-1.0.0.jar`

## How it works

Two small classes do the work, both outside the client's own packages:

- `com.betterblockesp.BetterBlockEspSettings` — adds the settings to the built-in module
  (`Module.add` is `protected`, so it is reached through reflection) and keeps a
  `Block -> color` map up to date.
- `com.betterblockesp.mixin.BetterBlockEspMixin` — two `@Redirect`s into `ModuleBlockEsp`:
  - `collectBoth` builds its scan list by reading the module's `blocks` setting. While the
    toggle is on, that value is swapped for the Colored Blocks list, so a block only has to
    be picked in one place.
  - `collect` hands each target to the box/tracer emitters. The color is swapped there,
    which keeps the chunk cache untouched: cached scans still store the module's base color
    and no scan is invalidated by a color change.

> The mixin package is declared as `com.betterblockesp.mixin` on purpose. Mixin treats a
> declared mixin package as owned territory and refuses to load any other class from it -
> putting the addon in `autismclient.modules` would break the client itself.
>
> `@At` targets also use the `Lowner;name(desc)Ret` form. This Mixin build rejects the
> older `Lowner;name:desc` form with `Invalid name: accept:`.

## License

MIT
