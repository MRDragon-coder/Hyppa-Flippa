package com.hypaflippa.market;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hypixel's official SkyBlock item list (no key needed): NPC sell prices and display names. */
public final class ItemsApiClient {
    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Gson GSON = new Gson();
    // HTTP/1.1 like the auction scan: the list is ~5 MB, and big HTTP/2 responses once arrived cut off.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final class ItemList {
        boolean success;
        List<Item> items = new ArrayList<>();
    }

    private static final class Item {
        String id;
        String name;
        double npc_sell_price;
    }

    /**
     * @param npcPrices NPC sell price per item id, for every item an NPC will buy
     * @param names     in-game display name per item id, colour codes removed
     */
    public record ItemInfo(Map<String, Double> npcPrices, Map<String, String> names) {}

    private ItemsApiClient() {}

    public static ItemInfo fetch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ITEMS_URL))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        ItemList list = GSON.fromJson(response.body(), ItemList.class);
        if (list == null || !list.success) throw new IllegalStateException("success=false");

        Map<String, Double> prices = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Item item : list.items) {
            if (item.id == null) continue;
            if (item.npc_sell_price > 0) prices.put(item.id, item.npc_sell_price);
            // "§a" style colour codes are part of Hypixel's names.
            if (item.name != null) names.put(item.id, item.name.replaceAll("§.", ""));
        }
        return new ItemInfo(Map.copyOf(prices), Map.copyOf(names));
    }
}
