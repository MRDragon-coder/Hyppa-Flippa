package com.hypaflippa.client.flips;

import com.hypaflippa.client.Feature;
import com.hypaflippa.client.MarketState;
import com.hypaflippa.client.MarketStatsCache;
import com.hypaflippa.flips.bazaar.BazaarFlip;
import com.hypaflippa.flips.bazaar.BazaarFlipperConfig;
import com.hypaflippa.flips.bazaar.FlipFilters;
import com.hypaflippa.flips.bazaar.NpcFlip;
import com.hypaflippa.market.bazaar.BazaarData;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Bazaar flips: the Orders page (buy order to sell offer) and the NPC page (bazaar to NPC). */
public final class BazaarFeature implements Feature {
    public final BazaarFlipperConfig config = new BazaarFlipperConfig();
    private List<BazaarFlip> flips; // null until the first bazaar fetch
    private List<NpcFlip> npcFlips; // null until both bazaar data and NPC prices are in

    public List<BazaarFlip> flips() { return flips; }
    public List<NpcFlip> npcFlips() { return npcFlips; }

    @Override
    public void load() {
        config.load();
    }

    @Override
    public void recompute() {
        BazaarData data = MarketState.data();
        if (data == null) return;
        flips = BazaarFlip.find(data, config.orders, config.maxSpreadRatio, MarketState::itemName, MarketStatsCache.STATS);
        if (MarketState.npcPrices() != null) {
            npcFlips = NpcFlip.find(data, MarketState.npcPrices(), config.npc, MarketState::itemName, MarketStatsCache.STATS);
        }
    }

    /** Only the open page's stats that are in use: a visible column, an active filter or the current sort. */
    @Override
    public void requestStats() {
        FlipFilters filters = config.isNpcPage() ? config.npc : config.orders;
        Set<MarketStatsCache.Kind> kinds = EnumSet.noneOf(MarketStatsCache.Kind.class);
        if (!filters.isColumnHidden("vol") || filters.maxVolatilityPercent != null || "vol".equals(filters.sortBy)) {
            kinds.add(MarketStatsCache.Kind.VOLATILITY);
        }
        if (!filters.isColumnHidden("outbid") || filters.minOutbidMinutes != null || "outbid".equals(filters.sortBy)) {
            kinds.add(MarketStatsCache.Kind.OUTBID);
        }
        if (config.isNpcPage()) {
            if (npcFlips != null) MarketStatsCache.request(npcFlips.stream().map(NpcFlip::productId).toList(), kinds);
        } else if (flips != null) {
            MarketStatsCache.request(flips.stream().map(BazaarFlip::getProductId).toList(), kinds);
        }
    }
}
