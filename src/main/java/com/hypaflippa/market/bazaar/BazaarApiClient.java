package com.hypaflippa.market.bazaar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Downloads the bazaar from Hypixel's API. */
public class BazaarApiClient {

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final Gson GSON = new GsonBuilder().create();
    // Shared and time-limited: a hung request would otherwise block every later refresh.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static BazaarData fetchBazaarData() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BAZAAR_URL))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        return GSON.fromJson(response.body(), BazaarData.class);
    }
}
