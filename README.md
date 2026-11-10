# Hypa Flippa

A client-side Fabric mod for Hypixel SkyBlock that helps you make coins: bazaar flips and
the expected profit of SkyBlock's gambles, live from the Hypixel API.

## Features

One draggable, resizable window with two sections of tabs.

**Flippa**
- **Bazaar**: order flips (buy order → sell offer) and NPC flips, with filters and sortable columns.
- **Craft**: recipes worth crafting, from the cheapest ingredients to what the result sells for
  on the bazaar or the Auction House.

**Gamba**
- **Jerry**: average return and profit per Jerry Box opening, with a full breakdown.
- **Gifts**: the same for White, Green, Red and Party Gifts (optionally counting both players' rolls).
- **Altar**: the most profitable dragon fragment to turn into Dragon Boots and sacrifice at the
  Draconic Altar, including the Mythic Endermite bonus.

The window appears by itself in the bazaar and jumps to the right tab on a box, gift or fragment
page. Press **H** (rebindable) to open it anywhere else. Commands: `/hypaflippa`, `/flips`.

## Credits

Price history for the volatility and outbid columns comes from [Coflnet](https://sky.coflnet.com).
Crafting recipes come from the [NotEnoughUpdates repo](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
(MIT license, included in the jar as `recipes-LICENSE.txt`). Everything else uses Hypixel's
public API.

## Building

Builds for Minecraft 26.1.2 and 26.2 from one codebase with
[Stonecutter](https://stonecutter.kikugie.dev/):

    ./gradlew buildAndCollect

The jars end up in `build/libs/<mod version>/`.

## Releasing

1. Raise `mod.version` in `stonecutter.properties.toml` and add a matching `## <version>`
   section to `CHANGELOG.md`.
2. Run `./gradlew updateRecipes` to refresh the bundled crafting recipes, and commit the
   changed `recipes.json` if there is one.
3. Run `./gradlew publishMods`. Without a Modrinth token (`modrinth.token` in your user-wide
   `~/.gradle/gradle.properties`) this is a dry run that uploads nothing.

## Bugs and ideas

Please use [GitHub Issues](https://github.com/MRDragon-coder/Hyppa-Flippa/issues).
