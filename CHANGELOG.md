# Changelog

Each release has a `## <version>` section. `./gradlew publishMods` uses the section of the
current version (`mod.version` in `stonecutter.properties.toml`) as the Modrinth changelog.

## 1.0.1

- The mod is now marked as client-only, so it no longer tries to load on servers.
- Added a "report a bug" link (GitHub Issues) to the mod's info, e.g. in Mod Menu.

## 1.0.0

First release.

- **Bazaar flips**: buy with a buy order, sell with a sell offer. Filters for margin and
  weekly volume, sortable columns.
- **NPC flips**: buy with a buy order or insta-buy, sell to an NPC.
- **Jerry Boxes and gifts**: average return and profit per opening, with a breakdown of
  every drop, priced from the bazaar and the Auction House.
- **Draconic Altar**: the most profitable dragon fragment to turn into boots and sacrifice,
  including the Mythic Endermite bonus.
- Opens with **H** or `/hypaflippa`, and appears by itself in the bazaar.
