# New-Swords

Minecraft Fabric mod: **New Swords**.

Current version content:

- Katana item
- Left click on an entity: short forward dash + exactly 5 damage dealt
- Right click: spectator mode for 5 seconds
- Right-click ability cooldown: 10 seconds

## Run In Dev

1. `./gradlew runClient`

## Build Jar

1. `./gradlew build`
2. Built jar is under `build/libs/`

## Notes

- This project targets Minecraft `1.20.1` with Fabric Loader.
- Katana model and language files are included.
- Add a texture at `src/main/resources/assets/newswords/textures/item/katana.png` to replace the missing-texture placeholder.