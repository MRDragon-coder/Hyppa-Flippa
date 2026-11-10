# Changelog

Each release has a `## <version>` section. `./gradlew publishMods` uses the section of the
current version (`mod.version` in `stonecutter.properties.toml`) as the Modrinth changelog.

## 1.1.0

- **Craft flips**: a new Craft tab lists recipes that are worth crafting: buy the ingredients,
  craft, sell the result. Ingredients come from wherever they're cheapest (bazaar, Auction
  House lowest BIN or an NPC shop), and the result is sold on the bazaar or at the lowest BIN,
  after tax and fees. Choose whether you buy and sell on the bazaar by order or instantly,
  filter on margin, profit, sales per day or number of listings (Auction House), and
  see which collection or slayer level a recipe needs. Turn off NPC buying to skip
  ingredients you'd have to fetch from an NPC shop, or show only recipes whose ingredients
  all fit in your sacks, so you can craft any amount straight from them. Hover a row to see
  every ingredient and its price. Like on the Bazaar tab there are Coins/h, Vol and Outbid
  columns for the crafted item (bazaar results; Outbid when you sell with a sell offer).
  "Buy/d" next to "Sold/d" shows how many you can get the ingredients for per day, limited by
  the slowest bazaar ingredient, so you can see which side holds you back. Right-click a row
  to open its recipe in SkyBlock's recipe book. Recipes come from the [NotEnoughUpdates repo](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
  and are built into the mod, so they work without an extra download.
- **Volatility**: a new "Vol" column on both Bazaar pages shows how much an item's price moved
  over the last day (green = calm, yellow = some movement, red = jumpy), and a "Vol" filter
  hides items that move more than you want. Big margins on jumpy items often vanish before
  your orders fill. Price history comes from [Coflnet](https://sky.coflnet.com).
- **Outbid time**: a new "Outbid" column shows how often the top orders got beaten during the
  last hour, so you can see whether a flip can be left alone or needs constant re-bidding
  (green = 10 min or more, yellow = 2-10 min, red = under 2 min). On the Orders page it's
  whichever of your buy order and sell offer gets beaten first; on the NPC page only the buy
  order counts. The "Out" filter hides items that get outbid more often than you want.
- **Coins per hour**: a new "Coins/h" column shows how many coins change hands per hour on the
  side that limits the flip (on the Orders page the slower of buying and selling, on the NPC
  page the buy-order side), from Hypixel's 7-day volumes. A small margin on a busy item can
  beat a big margin on a quiet one. The "C/h" filter hides items below a minimum.
- **Choose your columns**: the ≡ button on the Bazaar tab lists every column with a checkbox,
  so you can hide the ones you don't use. Each page remembers its own choice. Hiding Vol or
  Outbid also stops looking up that data (unless its filter or sort is in use), so the rest
  loads faster.
- **Help tooltips**: hover a filter, button or column header for a moment to see what it does.
- **Flippa and Gamba**: the tabs are now split into two sections, picked with a new bar at the
  top of the window: Flippa (Bazaar, Craft) and Gamba (Jerry, Gifts, Altar). Each section
  remembers the tab you had open.
- The window does less work while it's open: tables and gamble results are only worked out
  again when the prices or your settings change, not every frame.
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
