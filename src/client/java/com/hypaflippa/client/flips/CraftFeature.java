package com.hypaflippa.client.flips;

import com.hypaflippa.client.Feature;
import com.hypaflippa.client.MarketState;
import com.hypaflippa.client.MarketStatsCache;
import com.hypaflippa.flips.craft.CraftConfig;
import com.hypaflippa.flips.craft.CraftFlip;
import com.hypaflippa.flips.craft.CraftRecipes;
import com.hypaflippa.market.ItemPrices;
import com.hypaflippa.market.bazaar.BazaarPrices;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Craft flips: buy a recipe's ingredients, craft it, sell the result. */
public final class CraftFeature implements Feature {
    public final CraftConfig config = new CraftConfig();
    private List<CraftFlip> flips; // null until the first bazaar fetch

    public List<CraftFlip> flips() { return flips; }

    @Override
    public void load() {
        config.load();
    }

    @Override
    public void recompute() {
        if (MarketState.data() == null) return;
        flips = CraftFlip.find(CraftRecipes.get(), MarketState.data(), MarketState.auctions(), config,
                MarketState::itemName, MarketStatsCache.STATS);
    }

    /**
     * Only stats that are in use. Bazaar results have a price history; outbid time only matters
     * when you sell with a sell offer. Auction House results only have their sales per day.
     */
    @Override
    public void requestStats() {
        if (flips == null) return;
        Set<MarketStatsCache.Kind> kinds = EnumSet.noneOf(MarketStatsCache.Kind.class);
        if (!config.isColumnHidden("vol") || "vol".equals(config.sortBy)) kinds.add(MarketStatsCache.Kind.VOLATILITY);
        if ((!config.isColumnHidden("outbid") || "outbid".equals(config.sortBy)) && config.sellMode == BazaarPrices.Mode.ORDER) {
            kinds.add(MarketStatsCache.Kind.OUTBID);
        }
        MarketStatsCache.request(flips.stream()
                .filter(flip -> flip.sellSource() == ItemPrices.Source.BAZAAR).map(CraftFlip::id).toList(), kinds);
        if (!config.isColumnHidden("sales") || "sales".equals(config.sortBy) || config.minSalesPerDay > 0) {
            MarketStatsCache.request(flips.stream()
                    .filter(flip -> flip.sellSource() == ItemPrices.Source.AUCTION).map(CraftFlip::id).toList(),
                    EnumSet.of(MarketStatsCache.Kind.AUCTION_SALES));
        }
    }
}
