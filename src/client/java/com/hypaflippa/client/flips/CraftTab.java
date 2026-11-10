package com.hypaflippa.client.flips;

import com.hypaflippa.Format;
import com.hypaflippa.client.Feature;
import com.hypaflippa.client.Features;
import com.hypaflippa.client.MarketState;
import com.hypaflippa.client.MarketStatsCache.Kind;
import com.hypaflippa.client.MarketStatsCache;
import com.hypaflippa.client.ui.Controls;
import com.hypaflippa.client.ui.SortableTable.Column;
import com.hypaflippa.client.ui.SortableTable;
import com.hypaflippa.client.ui.Stepper;
import com.hypaflippa.client.ui.Tab;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.flips.craft.CraftConfig;
import com.hypaflippa.flips.craft.CraftFlip;
import com.hypaflippa.flips.craft.CraftRecipes;
import com.hypaflippa.market.ItemPrices;
import com.hypaflippa.market.bazaar.BazaarPrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Craft flips: buy a recipe's ingredients, craft it, sell the result. Recipes are bundled with
 * the mod (see {@link CraftRecipes}); prices are the live bazaar and Auction House ones. Hovering
 * a row lists its ingredients and where each one is cheapest; right-clicking it opens the recipe.
 */
public class CraftTab implements Tab {
    /** Sell prices of results that only sell on the Auction House. */
    private static final int AUCTION_COLOR = 0xFFFFAA00;

    private static final double[] PROFIT_STEPS = {0, 1_000, 5_000, 10_000, 25_000, 50_000, 100_000, 250_000,
            500_000, 1_000_000, 2_500_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000};
    private static final double[] SALES_STEPS = {0, 1, 5, 10, 25, 50, 100, 250, 500, 1_000, 5_000, 10_000};
    private static final double[] LISTED_STEPS = {1, 2, 3, 5, 10, 20, 50};

    private final Controls controls = new Controls(3, this::settingsChanged)
            .stepper(0, 4, new Stepper(48, () -> String.format(Locale.ROOT, "Min %.1f%%", config().minMarginPercent), (dir, shift) -> {
                CraftConfig c = config();
                c.minMarginPercent = Math.max(0, Math.round((c.minMarginPercent + dir * (shift ? 10 : 1)) * 10) / 10.0);
            }), () -> new Ui.Help("Min margin", "Hide crafts whose profit is less than this percentage of the ingredient cost."))
            .stepper(0, 80, new Stepper(48, () -> "P≥" + Format.coins(config().minProfit), (dir, shift) ->
                    config().minProfit = step(PROFIT_STEPS, config().minProfit, dir, shift)),
                    () -> new Ui.Help("Min profit", "Hide crafts that make fewer coins than this per craft."))
            .stepper(0, 156, new Stepper(48, () -> "Show " + config().maxResults, (dir, shift) -> {
                CraftConfig c = config();
                int step = shift ? 25 : 5;
                // Snap to multiples of the step: 1 -> 5 -> 10 ..., and never below 1.
                c.maxResults = dir > 0 ? (c.maxResults / step + 1) * step : Math.max(1, ((c.maxResults - 1) / step) * step);
            }), () -> new Ui.Help("Rows shown", "How many crafts to list at most, best first by the current sort."))
            .stepper(1, 4, new Stepper(48, () -> "Sold≥" + config().minSalesPerDay, (dir, shift) ->
                    config().minSalesPerDay = (int) step(SALES_STEPS, config().minSalesPerDay, dir, shift)),
                    () -> new Ui.Help("Min sold per day",
                            "Hide results that sell fewer than this many a day. Auction House results count once their sales have loaded."))
            .stepper(1, 80, new Stepper(48, () -> "Lst≥" + config().minListed, (dir, shift) ->
                    config().minListed = (int) step(LISTED_STEPS, config().minListed, dir, shift)),
                    () -> new Ui.Help("Min listings",
                            "Hide Auction House results with fewer BIN listings than this. With only one or two, the lowest BIN is just someone's asking price."))
            .button(1, 156, 48, () -> config().includeAuction ? "AH: on" : "AH: off", () -> config().includeAuction,
                    () -> new Ui.Help("Auction House results",
                            "On: also list crafts whose result only sells on the Auction House, at the lowest BIN after fees.\nOff: bazaar results only."),
                    () -> config().includeAuction = !config().includeAuction)
            .button(2, 4, 60, () -> "Buy: " + modeName(config().buyMode), () -> false,
                    () -> new Ui.Help("Buying ingredients",
                            "Order: priced as buy orders (cheaper, but you wait for them to fill).\nInsta: priced as insta-buys (dearer, but instant)."),
                    () -> config().buyMode = other(config().buyMode))
            .button(2, 66, 60, () -> "Sell: " + modeName(config().sellMode), () -> false,
                    () -> new Ui.Help("Selling the result",
                            "Order: priced as a sell offer (more coins, but you wait for a buyer).\nInsta: priced as an insta-sell (fewer coins, but instant).\nAuction House results always use the lowest BIN."),
                    () -> config().sellMode = other(config().sellMode))
            .button(2, 128, 60, () -> config().allowNpcShop ? "NPC: on" : "NPC: off", () -> config().allowNpcShop,
                    () -> new Ui.Help("NPC shop buying",
                            "On: ingredients that an NPC shop sells cheapest are bought there.\nOff: only the bazaar and Auction House, for when you'd rather not walk to the shop."),
                    () -> config().allowNpcShop = !config().allowNpcShop)
            .button(2, 190, 60, () -> config().sacksOnly ? "Sacks only" : "Sacks: any", () -> config().sacksOnly,
                    () -> new Ui.Help("Sacks only",
                            "Only list recipes whose ingredients all fit in your sacks, so you can craft any amount straight from them.\nSacks: any lists every recipe."),
                    () -> config().sacksOnly = !config().sacksOnly);

    private final SortableTable<CraftFlip> table = new SortableTable<>(controls.height(),
            CraftTab::config, Features.craft::flips, CraftTab::name,
            new Ui.Help("Name", "The crafted item. Hover a row for its ingredients; right-click it to open the recipe."),
            List.of(
                    new Column<>("cost", "Cost", flip -> Format.coins(flip.cost()), flip -> Ui.TEXT,
                            new Ui.Help("Cost", "What the ingredients of one craft cost, each bought wherever it's cheapest.")),
                    new Column<>("sell", "Sell", flip -> Format.coins(flip.sellValue()),
                            flip -> flip.sellSource() == ItemPrices.Source.AUCTION ? AUCTION_COLOR : Ui.TEXT,
                            new Ui.Help("Sell", "What one craft's result sells for, after bazaar tax or Auction House fees. Orange: it sells on the Auction House.")),
                    new Column<>("profit", "Profit", flip -> Format.signedCoins(flip.profit()), flip -> Ui.POSITIVE,
                            new Ui.Help("Profit", "Sell minus Cost: the coins you keep per craft.")),
                    new Column<>("margin", "Margin", flip -> String.format(Locale.ROOT, "%.1f%%", flip.marginPercent()), flip -> Ui.POSITIVE,
                            new Ui.Help("Margin", "Profit as a percentage of Cost.")),
                    new Column<>("coins", "Coins/h", flip -> flip.coinsPerHour() == null ? "-" : Format.coins(flip.coinsPerHour()),
                            flip -> flip.coinsPerHour() == null ? Ui.TEXT_FAINT : Ui.TEXT,
                            new Ui.Help("Coins/h", "Coins changing hands per hour in the result's bazaar market, on the side you sell to: roughly how much the market can take. Bazaar results only.")),
                    new Column<>("buyable", "Buy/d", flip -> perDay(flip.buyPerDay()),
                            flip -> flip.buyPerDay() == null ? Ui.TEXT_FAINT : Ui.TEXT,
                            new Ui.Help("Buy/d", "How many results' worth of ingredients you can buy per day, limited by the slowest bazaar ingredient. The lower of Buy/d and Sold/d is your daily limit.")),
                    new Column<>("sales", "Sold/d", CraftTab::salesCell,
                            flip -> flip.salesPerDay() == null ? Ui.TEXT_FAINT : Ui.TEXT,
                            new Ui.Help("Sold/d", "How many of the result sell per day: on the bazaar on the side you sell to, on the Auction House by Coflnet's count of ended auctions.")),
                    new Column<>("listed", "Listed", flip -> flip.listed() == null ? "-" : String.valueOf(flip.listed()),
                            flip -> flip.listed() == null ? Ui.TEXT_FAINT : Ui.TEXT,
                            new Ui.Help("Listed", "Auction House results: how many are listed as BIN right now. With only a few, the lowest BIN may not be a real market price.")),
                    new Column<>("vol", "Vol", flip -> flip.sellSource() == ItemPrices.Source.BAZAAR ? BazaarTab.volatilityText(flip.id()) : "-",
                            flip -> flip.sellSource() == ItemPrices.Source.BAZAAR
                                    ? BazaarTab.volatilityColor(MarketStatsCache.STATS.volatilityPercent(flip.id())) : Ui.TEXT_FAINT,
                            new Ui.Help("Vol", "How much the result's price swung over the last day. Green: calm, yellow: some movement, red: jumpy. Bazaar results only.")),
                    new Column<>("outbid", "Outbid", CraftTab::outbidCell,
                            flip -> BazaarTab.outbidColor(flip.outbidMinutes(MarketStatsCache.STATS, config().sellMode)),
                            new Ui.Help("Outbid", "How long a sell offer for the result typically stays cheapest before someone undercuts it (last hour). Green: place it and go, red: you'll keep relisting. Only with Sell: Order.")),
                    new Column<>("requires", "Requires", flip -> flip.recipe().requires() == null ? "-" : flip.recipe().requires(),
                            flip -> flip.recipe().requires() == null ? Ui.TEXT_FAINT : Ui.TEXT_DIM,
                            new Ui.Help("Requires", "The collection or slayer level that unlocks the recipe."))),
            "No profitable crafts found.", CraftTab::waitingMessage, this::settingsChanged);

    private static CraftConfig config() {
        return Features.craft.config;
    }

    @Override public String id() { return "craft"; }
    @Override public String title() { return "Craft"; }
    @Override public Feature feature() { return Features.craft; }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        if (!table.click(Minecraft.getInstance().font, area, mx, my)) controls.click(area, mx, my, shift);
    }

    /** Right-clicking a row opens its recipe in SkyBlock's recipe book. */
    @Override
    public void mouseRightClicked(Ui.Rect area, double mx, double my) {
        CraftFlip flip = table.rowAt(area, mx, my);
        if (flip == null) return;
        String id = recipeBookId(flip.id());
        var connection = Minecraft.getInstance().getConnection();
        if (id != null && connection != null) connection.sendCommand("viewrecipe " + id);
    }

    /**
     * The SkyBlock item id {@code /viewrecipe} takes, or null when it has none: books and pets are
     * priced under made-up keys (ENCHANTMENT_..., PET:...) that aren't real item ids.
     */
    private static String recipeBookId(String id) {
        return id.startsWith("ENCHANTMENT_") || id.startsWith("PET:") ? null : id;
    }

    @Override
    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        table.mouseScrolled(area, mx, my, deltaY);
    }

    private void settingsChanged() {
        config().save();
        Features.changed(Features.craft);
        table.resetScroll();
    }

    // --- Steps and cells

    private static BazaarPrices.Mode other(BazaarPrices.Mode mode) {
        return mode == BazaarPrices.Mode.ORDER ? BazaarPrices.Mode.INSTANT : BazaarPrices.Mode.ORDER;
    }

    /** Moves a value along a list of steps (shift jumps three); below the first step stays at the first. */
    private static double step(double[] steps, double value, int direction, boolean shift) {
        int index = 0;
        while (index < steps.length - 1 && steps[index] < value) index++;
        index = Math.clamp(index + direction * (shift ? 3 : 1), 0, steps.length - 1);
        return steps[index];
    }

    /** How soon a sell offer for the result gets undercut; "-" when you don't sell with an offer. */
    private static String outbidCell(CraftFlip flip) {
        if (flip.sellSource() != ItemPrices.Source.BAZAAR || config().sellMode != BazaarPrices.Mode.ORDER) return "-";
        return switch (MarketStatsCache.status(Kind.OUTBID, flip.id())) {
            case LOADING -> "...";
            case NO_DATA -> "-";
            case KNOWN -> BazaarTab.formatMinutes(flip.outbidMinutes(MarketStatsCache.STATS, config().sellMode));
        };
    }

    /** Items a day: "..." while an Auction House result's sales are loading. */
    private static String salesCell(CraftFlip flip) {
        return flip.salesPerDay() == null && flip.sellSource() == ItemPrices.Source.AUCTION
                && MarketStatsCache.status(Kind.AUCTION_SALES, flip.id()) == MarketStatsCache.Status.LOADING
                ? "..." : perDay(flip.salesPerDay());
    }

    /** Items a day: "-" when unknown, "<1" for slow items (Format.coins would round them to 0). */
    private static String perDay(Double value) {
        if (value == null) return "-";
        return value > 0 && value < 1 ? "<1" : Format.coins(value);
    }

    private static String waitingMessage() {
        if (CraftRecipes.get().recipes().isEmpty()) return "No recipes bundled with this version.";
        return MarketState.error() != null ? MarketState.error() : "Loading...";
    }

    private static String name(CraftFlip flip) {
        String name = MarketState.itemName(flip.id());
        return flip.recipe().count() > 1 ? flip.recipe().count() + "× " + name : name;
    }

    private static String modeName(BazaarPrices.Mode mode) {
        return mode == BazaarPrices.Mode.ORDER ? "Order" : "Insta";
    }

    // --- Rendering

    @Override
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        controls.render(guis, font, area, mouseX, mouseY);
        table.render(guis, font, area, mouseX, mouseY);
        if (table.isPickerOpen()) return;
        CraftFlip hoveredRow = table.rowAt(area, mouseX, mouseY);
        if (hoveredRow != null) {
            renderDetails(guis, font, area, mouseX, mouseY, hoveredRow);
            return;
        }
        Ui.Help help = controls.helpAt(area, mouseX, mouseY);
        if (help == null) help = table.helpAt(font, area, mouseX, mouseY);
        if (help != null) Ui.helpTooltip(guis, font, area, mouseX, mouseY, help);
    }

    /**
     * The hover box: every ingredient with its amount, price and where it's cheapest, then what
     * the result sells for and where.
     * <pre>
     *   Enchanted Diamond Block
     *   160× Enchanted Diamond   1,234.5   197.5k  BZ
     *   Sells for 220.1k on the bazaar (297 a day)
     *   Profit +22.6k
     * </pre>
     */
    private static void renderDetails(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY, CraftFlip flip) {
        List<String[]> lines = new ArrayList<>(); // name, unit price, total, source
        for (CraftFlip.Ingredient ingredient : flip.ingredients()) {
            lines.add(new String[] {ingredient.count() + "× " + MarketState.itemName(ingredient.id()),
                    Format.exactCoins(ingredient.unitPrice()), Format.coins(ingredient.total()),
                    sourceTag(ingredient.source()) + (ingredient.perDay() == null ? "" : " " + perDay(ingredient.perDay()) + "/d")});
        }
        String sells = flip.sellSource() == ItemPrices.Source.AUCTION
                ? "Sells for " + Format.coins(flip.sellValue()) + " on the AH (" + flip.listed() + " listed, "
                        + (flip.salesPerDay() == null ? "" : perDay(flip.salesPerDay()) + " a day, ") + "after fees)"
                : "Sells for " + Format.coins(flip.sellValue()) + " on the bazaar ("
                        + perDay(flip.salesPerDay()) + " a day, after tax)";
        String profit = "Profit " + Format.signedCoins(flip.profit()) + String.format(Locale.ROOT, " (%.1f%%)", flip.marginPercent());
        String requires = flip.recipe().requires() == null ? null : "Requires " + flip.recipe().requires();

        int[] widths = new int[4];
        for (String[] line : lines) {
            for (int i = 0; i < 4; i++) widths[i] = Math.max(widths[i], font.width(line[i]));
        }
        int gap = 8;
        int width = widths[0] + widths[1] + widths[2] + widths[3] + 3 * gap;
        width = Math.max(width, Math.max(font.width(name(flip)), Math.max(font.width(sells), font.width(profit))));
        if (requires != null) width = Math.max(width, font.width(requires));
        String hint = recipeBookId(flip.id()) == null ? null : "Right-click to view the recipe";
        if (hint != null) width = Math.max(width, font.width(hint));
        int lineHeight = 10;
        int lineCount = lines.size() + 3 + (requires != null ? 1 : 0) + (hint != null ? 1 : 0);
        int[] at = Ui.hoverBox(guis, area, mouseX, mouseY, width, lineCount * lineHeight);
        int textX = at[0], lineY = at[1];
        guis.text(font, name(flip), textX, lineY, Ui.ACCENT);
        lineY += lineHeight;
        for (String[] line : lines) {
            guis.text(font, line[0], textX, lineY, Ui.TEXT);
            int right = textX + widths[0] + gap + widths[1];
            Ui.textRight(guis, font, line[1], right, lineY, Ui.TEXT_DIM);
            right += gap + widths[2];
            Ui.textRight(guis, font, line[2], right, lineY, Ui.TEXT);
            guis.text(font, line[3], right + gap, lineY, Ui.TEXT_FAINT);
            lineY += lineHeight;
        }
        guis.text(font, sells, textX, lineY, flip.sellSource() == ItemPrices.Source.AUCTION ? AUCTION_COLOR : Ui.TEXT);
        lineY += lineHeight;
        guis.text(font, profit, textX, lineY, Ui.signColor(flip.profit()));
        if (requires != null) {
            lineY += lineHeight;
            guis.text(font, requires, textX, lineY, Ui.TEXT_DIM);
        }
        if (hint != null) guis.text(font, hint, textX, lineY + lineHeight, Ui.TEXT_FAINT);
    }

    private static String sourceTag(CraftFlip.Source source) {
        return switch (source) {
            case BAZAAR -> "BZ";
            case AUCTION -> "AH";
            case NPC -> "NPC";
        };
    }
}
