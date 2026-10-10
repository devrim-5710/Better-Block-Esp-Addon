# Better ESP - Autism Client Addon

Adds a **Box** render mode to Autism Client's built-in ESP and ItemESP modules.

## Features

- **Box Mode** for ESP — draws 3D bounding boxes around players and mobs
- **Box Mode** for ItemESP — draws 3D bounding boxes around dropped items
- **Exact hitbox size** — boxes match the entity's actual collision box
- **Smooth rendering** — interpolated positions eliminate stutter during movement
- **Per-entity colors** — uses the same color settings as the 2D/Shader modes
- **Fill support** — toggleable translucent fill (75% transparent) for both ESP and ItemESP
- **Through walls** — boxes render behind blocks (no depth test)

## Installation

1. Install [Autism Client](https://github.com/) for Minecraft
2. Download `autism-better-esp-1.0.0.jar` from [Releases](https://github.com/)
3. Place the JAR into your `mods` folder
4. Launch the game

## Usage

1. Open Autism Client settings
2. Go to **ESP** or **ItemESP** module
3. Change **Mode** from `2D` / `Shader` to `Box`
4. Toggle **Fill** to enable/disable translucent box fill
5. Customize colors in the Colors section (same as 2D mode)

## Requirements

- Minecraft (with Autism Client)
- Fabric Loader
- Java 25+

## Build

```bash
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-25"
.\gradlew.bat clean build
```

Output: `build/libs/autism-better-esp-1.0.0.jar`

## How It Works

The addon uses reflection to inject `Box` as a third option into the existing `ChoiceSetting` of ESP and ItemESP modules. Rendering is done through Fabric's `LevelRenderEvents.COLLECT_SUBMITS` API, drawing wireframe boxes using `AutismRenderTypes.storageEspLinesSeeThrough()` (no depth test) and `AutismWorldGeometry.line()`.

## License

MIT
