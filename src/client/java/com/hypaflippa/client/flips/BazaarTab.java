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
import com.hypaflippa.flips.bazaar.BazaarFlip;
import com.hypaflippa.flips.bazaar.BazaarFlipperConfig;
import com.hypaflippa.flips.bazaar.FlipFilters;
import com.hypaflippa.flips.bazaar.NpcFlip;
import com.hypaflippa.market.MarketStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Bazaar flips, on two pages that share the same controls:
 * <ul>
 *   <li>Orders: buy with a buy order, sell with a sell offer, keep the gap.</li>
 *   <li>NPC: buy with a buy order or insta-buy, sell to an NPC.</li>
 * </ul>
 * Each page has its own filters, sort order and columns.
 */
public class BazaarTab implements Tab {
    private static final int[] VOLUME_STEPS = {0, 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000,
            500_000, 1_000_000, 5_000_000, 10_000_000};
    private static final double[] OUTBID_STEPS = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60};
    private static final double[] COINS_STEPS = {10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000,
            2_500_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000};
    private static final Ui.Help VOL_HELP = new Ui.Help("Vol",
            "How much the price swung over the last day. Green: calm, yellow: some movement, red: jumpy.");

    private final Controls controls = new Controls(3, this::settingsChanged);
    private final SortableTable<BazaarFlip> orders = new SortableTable<>(controls.height(),
            () -> config().orders, Features.bazaar::flips, flip -> MarketState.itemName(flip.getProductId()),
            new Ui.Help("Name", "The bazaar item to flip."),
            List.of(
                    new Column<>("buy", "Buy", flip -> Format.exactCoins(flip.getBuyPrice()), flip -> Ui.TEXT,
                            new Ui.Help("Buy", "The current best buy order: about what you pay when you place yours at the top.")),
                    new Column<>("sell", "Sell", flip -> Format.exactCoins(flip.getSellPrice()), flip -> Ui.TEXT,
                            new Ui.Help("Sell", "The current cheapest sell offer: about what you get when you place yours at the front.")),
                    new Column<>("margin", "Margin", flip -> percent(flip.getProfitPercent()), flip -> Ui.POSITIVE,
                            new Ui.Help("Margin", "Profit per item after the bazaar's tax, as a percentage of the buy price.")),
                    coinsColumn(BazaarFlip::coinsPerHour, new Ui.Help("Coins/h",
                            "Coins per hour flowing through the slower side of the flip (insta-sells filling buy orders, or insta-buys filling sell offers), over the last week: roughly how big the flip can get.")),
                    volatilityColumn(BazaarFlip::getProductId),
                    outbidColumn(BazaarFlip::getProductId, BazaarFlip::outbidMinutes, new Ui.Help("Outbid",
                            "How long until your buy order or sell offer typically gets beaten, whichever comes first (last hour). Green: place it and go, red: you'll keep re-bidding."))),
            "No profitable flips found.", BazaarTab::bazaarWaitingMessage, this::settingsChanged);

    private final SortableTable<NpcFlip> npc = new SortableTable<>(controls.height(),
            () -> config().npc, Features.bazaar::npcFlips, flip -> MarketState.itemName(flip.productId()),
            new Ui.Help("Name", "A bazaar item an NPC buys for more than it costs on the bazaar."),
            List.of(
                    new Column<>("order", "Order", flip -> Format.exactCoins(flip.buyOrderPrice()), flip -> Ui.TEXT,
                            new Ui.Help("Order", "The current best buy order: about what you pay when you place yours at the top.")),
                    new Column<>("insta", "Insta", flip -> flip.hasInstaBuy() ? Format.exactCoins(flip.instaBuyPrice()) : "-", flip -> Ui.TEXT,
                            new Ui.Help("Insta", "What insta-buying costs right now: the cheapest sell offer. \"-\" when nobody is selling.")),
                    new Column<>("npc", "NPC", flip -> Format.exactCoins(flip.npcPrice()), flip -> Ui.TEXT,
                            new Ui.Help("NPC", "What an NPC pays per item. NPCs buy any amount and take no tax.")),
                    new Column<>("orderMargin", "Order %", flip -> percent(flip.orderMarginPercent()), flip -> Ui.POSITIVE,
                            new Ui.Help("Order %", "Profit when you buy with a buy order and sell to an NPC, as a percentage of the buy price.")),
                    new Column<>("instaMargin", "Insta %", flip -> flip.hasInstaBuy() ? percent(flip.instaMarginPercent()) : "-",
                            flip -> flip.hasInstaBuy() ? Ui.signColor(flip.instaMarginPercent()) : Ui.TEXT_FAINT,
                            new Ui.Help("Insta %", "Profit when you insta-buy and sell to an NPC, as a percentage of the price. Red: you'd lose coins.")),
                    coinsColumn(NpcFlip::coinsPerHour, new Ui.Help("Coins/h",
                            "Coins per hour flowing into buy orders (players insta-selling), over the last week: roughly how fast your order can fill.")),
                    volatilityColumn(NpcFlip::productId),
                    outbidColumn(NpcFlip::productId, NpcFlip::outbidMinutes, new Ui.Help("Outbid",
                            "How long your buy order typically stays the best before someone bids higher (last hour). Green: place it and go, red: you'll keep re-bidding."))),
            "No profitable flips found.",
            () -> MarketState.data() == null ? bazaarWaitingMessage() : "Loading NPC prices...", this::settingsChanged);

    public BazaarTab() {
        // The same settings as the /flips arguments, applied to whichever page is open (sorting is
        // done by clicking column headers), plus the NPC page's insta-margin filter.
        Stepper outbid = new Stepper(48, () -> filters().minOutbidMinutes == null ? "Out off"
                : String.format(Locale.ROOT, "Out≥%.0fm", filters().minOutbidMinutes), (dir, shift) -> {
            FlipFilters f = filters();
            // Either button turns it on at 5 minutes; past either end of OUTBID_STEPS turns it off.
            f.minOutbidMinutes = f.minOutbidMinutes == null ? Double.valueOf(5.0) : stepOrOff(OUTBID_STEPS, f.minOutbidMinutes, dir, shift);
        });
        controls
                .stepper(0, 4, new Stepper(48, () -> String.format(Locale.ROOT, "%s %.1f%%",
                        config().isNpcPage() ? "Ord" : "Min", filters().minProfitPercent), (dir, shift) -> {
                    FlipFilters f = filters();
                    f.minProfitPercent = Math.max(0, Math.round((f.minProfitPercent + dir * (shift ? 5 : 0.5)) * 10) / 10.0);
                }), () -> config().isNpcPage()
                        ? new Ui.Help("Min order margin", "Hide items that make less than this percentage when bought with a buy order and sold to an NPC.")
                        : new Ui.Help("Min margin", "Hide flips whose margin (profit after tax, as a percentage of the buy price) is below this."))
                .button(0, 82, 48, () -> "Orders", () -> !config().isNpcPage(),
                        () -> new Ui.Help("Orders", "Buy with a buy order, sell with a sell offer, keep the difference."),
                        () -> config().page = BazaarFlipperConfig.PAGE_ORDERS)
                .button(0, 132, 48, () -> "NPC", () -> config().isNpcPage(),
                        () -> new Ui.Help("NPC", "Buy on the bazaar, sell to an NPC."),
                        () -> config().page = BazaarFlipperConfig.PAGE_NPC)
                .stepper(0, 184, new Stepper(48, () -> filters().minCoinsPerHour == null ? "C/h off"
                        : "C/h≥" + Format.coins(filters().minCoinsPerHour), (dir, shift) -> {
                    FlipFilters f = filters();
                    // Either button turns it on at 1M/h; past either end of COINS_STEPS turns it off.
                    f.minCoinsPerHour = f.minCoinsPerHour == null ? Double.valueOf(1_000_000) : stepOrOff(COINS_STEPS, f.minCoinsPerHour, dir, shift);
                }), () -> new Ui.Help("Min coins per hour", "Hide items whose Coins/h is below this.\nOff: no limit."))
                .stepper(1, 4, new Stepper(48, () -> "Show " + filters().maxResults, (dir, shift) -> {
                    FlipFilters f = filters();
                    int step = shift ? 25 : 5;
                    // Snap to multiples of the step: 1 -> 5 -> 10 ..., and never below 1.
                    f.maxResults = dir > 0 ? (f.maxResults / step + 1) * step : Math.max(1, ((f.maxResults - 1) / step) * step);
                }), () -> new Ui.Help("Rows shown", "How many items to list at most, best first by the current sort."))
                .stepper(1, 80, new Stepper(48, () -> "Sell≥" + compact(filters().minSellMovingWeek), (dir, shift) ->
                        filters().minSellMovingWeek = stepVolume(filters().minSellMovingWeek, dir, shift)),
                        () -> new Ui.Help("Min weekly insta-sells",
                                "Hide items that fewer than this many get insta-sold per week. Insta-sells are what fill buy orders."))
                .stepper(1, 156, new Stepper(48, () -> "Buy≥" + compact(filters().minBuyMovingWeek), (dir, shift) ->
                        filters().minBuyMovingWeek = stepVolume(filters().minBuyMovingWeek, dir, shift)),
                        () -> new Ui.Help("Min weekly insta-buys",
                                "Hide items that fewer than this many get insta-bought per week. Insta-buys are what fill sell offers."))
                .stepper(2, 4, new Stepper(48, () -> filters().maxVolatilityPercent == null ? "Vol off"
                        : String.format(Locale.ROOT, "Vol≤%.0f%%", filters().maxVolatilityPercent), (dir, shift) -> {
                    FlipFilters f = filters();
                    // Either button turns it on at 10%; then whole percents (shift: 5) between 1% and
                    // 50%, and stepping past either end turns it off again.
                    if (f.maxVolatilityPercent == null) {
                        f.maxVolatilityPercent = 10.0;
                    } else {
                        double next = f.maxVolatilityPercent + dir * (shift ? 5 : 1);
                        f.maxVolatilityPercent = next < 1 || next > 50 ? null : next;
                    }
                }), () -> new Ui.Help("Max volatility", "Hide items whose price swung more than this over the last day.\nOff: no limit."))
                .stepper(2, 80, new Stepper(48, () -> filters().minInstaProfitPercent == null ? "Ins off"
                        : String.format(Locale.ROOT, "Ins %.1f%%", filters().minInstaProfitPercent), (dir, shift) -> {
                    FlipFilters f = filters();
                    // Off below 0%: insta margins are often negative, and "off" keeps buy-order-only flips.
                    if (f.minInstaProfitPercent == null) {
                        if (dir > 0) f.minInstaProfitPercent = 0.0;
                    } else {
                        double next = Math.round((f.minInstaProfitPercent + dir * (shift ? 5 : 0.5)) * 10) / 10.0;
                        f.minInstaProfitPercent = next < 0 ? null : next;
                    }
                }), () -> new Ui.Help("Min insta margin",
                        "Hide items that make less than this percentage when insta-bought and sold to an NPC.\nOff: items you can only flip with a buy order stay too."),
                        () -> config().isNpcPage())
                // One stepper in two places: next to "Ins" on the NPC page, in its spot on the Orders page.
                .stepper(2, 156, outbid, BazaarTab::outbidHelp, () -> config().isNpcPage())
                .stepper(2, 80, outbid, BazaarTab::outbidHelp, () -> !config().isNpcPage());
    }

    private static BazaarFlipperConfig config() {
        return Features.bazaar.config;
    }

    private static FlipFilters filters() {
        return config().isNpcPage() ? config().npc : config().orders;
    }

    private SortableTable<?> table() {
        return config().isNpcPage() ? npc : orders;
    }

    @Override public String id() { return "bazaar"; }
    @Override public String title() { return "Bazaar"; }
    @Override public Feature feature() { return Features.bazaar; }

    private static Ui.Help outbidHelp() {
        return config().isNpcPage()
                ? new Ui.Help("Min outbid time", "Hide items where your buy order typically gets outbid sooner than this.\nOff: no limit.")
                : new Ui.Help("Min outbid time", "Hide items where your buy order or sell offer typically gets beaten sooner than this.\nOff: no limit.");
    }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        if (!table().click(Minecraft.getInstance().font, area, mx, my)) controls.click(area, mx, my, shift);
    }

    @Override
    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        table().mouseScrolled(area, mx, my, deltaY);
    }

    private void settingsChanged() {
        config().save();
        Features.changed(Features.bazaar);
        table().resetScroll();
    }

    // --- Rendering

    @Override
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        SortableTable<?> table = table();
        controls.render(guis, font, area, mouseX, mouseY);
        table.render(guis, font, area, mouseX, mouseY);
        if (table.isPickerOpen()) return;
        Ui.Help help = controls.helpAt(area, mouseX, mouseY);
        if (help == null) help = table.helpAt(font, area, mouseX, mouseY);
        if (help != null) Ui.helpTooltip(guis, font, area, mouseX, mouseY, help);
    }

    // --- Steps and cells

    /** Moves a volume filter along VOLUME_STEPS (shift jumps three steps). */
    private static int stepVolume(int value, int direction, boolean shift) {
        for (int n = shift ? 3 : 1; n > 0; n--) {
            int next = value;
            if (direction > 0) {
                for (int step : VOLUME_STEPS) {
                    if (step > value) { next = step; break; }
                }
            } else {
                next = 0;
                for (int step : VOLUME_STEPS) {
                    if (step < value) next = step;
                }
            }
            value = next;
        }
        return value;
    }

    /** Moves along steps (shift jumps three); stepping past either end turns the filter off (null). */
    private static Double stepOrOff(double[] steps, double value, int direction, boolean shift) {
        int index = 0;
        while (index < steps.length - 1 && steps[index] < value) index++;
        index += direction * (shift ? 3 : 1);
        return index < 0 || index >= steps.length ? null : steps[index];
    }

    /** 1500 -> "1.5k", 2000000 -> "2M". */
    private static String compact(int value) {
        if (value >= 1_000_000) return trimDecimal(value / 1_000_000.0) + "M";
        if (value >= 1_000) return trimDecimal(value / 1_000.0) + "k";
        return String.valueOf(value);
    }

    private static String trimDecimal(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** Coins changing hands per hour on the side that limits the flip (Hypixel's 7-day volumes). */
    private static <R> Column<R> coinsColumn(ToDoubleFunction<R> coinsPerHour, Ui.Help help) {
        return new Column<>("coins", "Coins/h", row -> Format.coins(coinsPerHour.applyAsDouble(row)), row -> Ui.TEXT, help);
    }

    /** Price volatility over the last day (Coflnet): green = calm, yellow = some movement, red = jumpy. */
    private static <R> Column<R> volatilityColumn(Function<R, String> productId) {
        return new Column<>("vol", "Vol", row -> volatilityText(productId.apply(row)),
                row -> volatilityColor(MarketStatsCache.STATS.volatilityPercent(productId.apply(row))), VOL_HELP);
    }

    static String volatilityText(String productId) {
        return switch (MarketStatsCache.status(Kind.VOLATILITY, productId)) {
            case LOADING -> "...";
            case NO_DATA -> "-";
            case KNOWN -> String.format(Locale.ROOT, "±%.1f%%", MarketStatsCache.STATS.volatilityPercent(productId));
        };
    }

    static int volatilityColor(Double percent) {
        if (percent == null) return Ui.TEXT_FAINT;
        return percent <= 2 ? Ui.POSITIVE : percent <= 5 ? Ui.ACCENT : Ui.NEGATIVE;
    }

    /**
     * Minutes until the page's order(s) typically get outbid (last hour, Coflnet): red = you'd be
     * re-bidding constantly, yellow = check back now and then, green = place it and go.
     *
     * @param minutesOf the page's rule: Orders = faster of buy order and sell offer, NPC = buy order
     */
    private static <R> Column<R> outbidColumn(Function<R, String> productId, BiFunction<MarketStats, String, Double> minutesOf,
                                              Ui.Help help) {
        return new Column<>("outbid", "Outbid", row -> {
            String id = productId.apply(row);
            return switch (MarketStatsCache.status(Kind.OUTBID, id)) {
                case LOADING -> "...";
                case NO_DATA -> "-";
                case KNOWN -> formatMinutes(minutesOf.apply(MarketStatsCache.STATS, id));
            };
        }, row -> outbidColor(minutesOf.apply(MarketStatsCache.STATS, productId.apply(row))), help);
    }

    static int outbidColor(Double minutes) {
        if (minutes == null) return Ui.TEXT_FAINT;
        return minutes >= 10 ? Ui.POSITIVE : minutes >= 2 ? Ui.ACCENT : Ui.NEGATIVE;
    }

    /** "<1m", "~3m", "~45m", ">1h". The source covers one hour in 20-second steps. */
    static String formatMinutes(double minutes) {
        if (minutes >= 60) return ">1h";
        if (minutes < 1) return "<1m";
        return "~" + Math.round(minutes) + "m";
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String bazaarWaitingMessage() {
        return MarketState.error() != null ? MarketState.error() : "Loading...";
    }
}
