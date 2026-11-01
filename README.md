# Hypa Flippa

A client-side Fabric mod for Hypixel SkyBlock that helps you make coins: bazaar flips and
the expected profit of SkyBlock's gambles, live from the Hypixel API.

## Features

One draggable, resizable window with tabs:

- **Bazaar**: order flips (buy order → sell offer) with filters and sortable columns.
- **Jerry**: average return and profit per Jerry Box opening, with a full breakdown.
- **Gifts**: the same for White, Green, Red and Party Gifts (optionally counting both players' rolls).
- **Altar**: the most profitable dragon fragment to turn into Dragon Boots and sacrifice at the
  Draconic Altar, including the Mythic Endermite bonus.

The window appears by itself in the bazaar and jumps to the right tab on a box, gift or fragment
page. Press **H** (rebindable) to open it anywhere else. Commands: `/hypaflippa`, `/flips`.

## Building

Builds for Minecraft 26.1.2 and 26.2 from one codebase with
[Stonecutter](https://stonecutter.kikugie.dev/):

    ./gradlew buildAndCollect

The jars end up in `build/libs/<mod version>/`.
