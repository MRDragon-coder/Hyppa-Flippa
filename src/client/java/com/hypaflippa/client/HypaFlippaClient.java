package com.hypaflippa.client;

import com.hypaflippa.HypaFlippa;
import com.hypaflippa.client.flips.BazaarTab;
import com.hypaflippa.client.flips.CraftTab;
import com.hypaflippa.client.gamble.AltarTab;
import com.hypaflippa.client.gamble.GambleTab;
import com.hypaflippa.client.ui.HypaWindow;
import com.hypaflippa.client.ui.Section;
import com.hypaflippa.flips.bazaar.BazaarFlipperConfig;
import com.hypaflippa.flips.bazaar.FlipFilters;
import com.hypaflippa.gamble.GambleData;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

public class HypaFlippaClient implements ClientModInitializer {
    private static HypaWindow window;
    private static KeyMapping toggleKey;

    /** The one Hypa Flippa window, shared by every place it can appear. */
    public static HypaWindow window() {
        return window;
    }

    /** Opens/closes the window (H by default, rebindable under Controls > Hypa Flippa). */
    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    @Override
    public void onInitializeClient() {
        Features.load();
        window = new HypaWindow(List.of(
                new Section("flippa", "Flippa", List.of(
                        new BazaarTab(),
                        new CraftTab())),
                new Section("gamba", "Gamba", List.of(
                        new GambleTab("jerry", "Jerry", GambleData::jerryBoxes, false),
                        new GambleTab("gifts", "Gifts", GambleData::gifts, true),
                        new AltarTab()))
        ));

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(HypaFlippa.MOD_ID, "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.hypaflippa.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_H, category));
        // Outside menus the key is handled here; inside menus WindowOverlay and HypaFlippaScreen
        // handle it, because open screens receive key presses instead of key mappings.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Features.tick();
            while (toggleKey.consumeClick()) {
                if (McCompat.currentScreen(client) == null) McCompat.setScreen(client, new HypaFlippaScreen());
            }
        });

        WindowOverlay.register();

        // Every level runs the same handler; it reads whichever arguments were typed.
        Command<FabricClientCommandSource> flips = HypaFlippaClient::runFlips;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("hypaflippa").executes(context -> {
                openWindow(context.getSource().getClient());
                return 1;
            }));
            dispatcher.register(
                ClientCommands.literal("flips").executes(flips)
                    .then(ClientCommands.argument("profit", DoubleArgumentType.doubleArg(0)).executes(flips)
                        .then(ClientCommands.argument("maxResults", IntegerArgumentType.integer(1)).executes(flips)
                            .then(ClientCommands.argument("minSellVol", IntegerArgumentType.integer(0)).executes(flips)
                                .then(ClientCommands.argument("minBuyVol", IntegerArgumentType.integer(0)).executes(flips)
                                    .then(ClientCommands.argument("sortBy", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(BazaarFlipperConfig.ORDER_SORT_MODES, builder))
                                        .executes(flips)
                                    )
                                )
                            )
                        )
                    )
            );
        });
    }

    private static int runFlips(CommandContext<FabricClientCommandSource> context) {
        // The config was loaded from disk at startup. Arguments typed now override it and are
        // saved; arguments left out keep their saved values instead of resetting to defaults.
        // /flips is about order flips, so it always opens (and sets) the Orders page.
        BazaarFlipperConfig bazaar = Features.bazaar.config;
        bazaar.page = BazaarFlipperConfig.PAGE_ORDERS;
        FlipFilters config = bazaar.orders;

        String sortBy = optionalArgument(context, "sortBy", String.class);
        if (sortBy != null) {
            sortBy = sortBy.toLowerCase(Locale.ROOT);
            if (!BazaarFlipperConfig.ORDER_SORT_MODES.contains(sortBy)) {
                context.getSource().sendError(Component.literal("Unknown sort '" + sortBy + "', use one of " + BazaarFlipperConfig.ORDER_SORT_MODES));
                return 0;
            }
            config.sortBy = sortBy;
            config.sortAscending = FlipFilters.defaultAscending(sortBy);
        }
        Double profit = optionalArgument(context, "profit", Double.class);
        if (profit != null) config.minProfitPercent = profit;
        Integer maxResults = optionalArgument(context, "maxResults", Integer.class);
        if (maxResults != null) config.maxResults = maxResults;
        Integer minSellVol = optionalArgument(context, "minSellVol", Integer.class);
        if (minSellVol != null) config.minSellMovingWeek = minSellVol;
        Integer minBuyVol = optionalArgument(context, "minBuyVol", Integer.class);
        if (minBuyVol != null) config.minBuyMovingWeek = minBuyVol;
        bazaar.save();

        Features.changed(Features.bazaar); // instant if we already have data
        MarketState.refresh();

        window.showTab("bazaar");
        openWindow(context.getSource().getClient());
        return 1;
    }

    private static void openWindow(Minecraft client) {
        // schedule() runs next frame. Opening the screen right away would be undone by the
        // chat screen closing itself after the command finishes.
        client.schedule(() -> McCompat.setScreen(client, new HypaFlippaScreen()));
    }

    private static <T> T optionalArgument(CommandContext<?> context, String name, Class<T> type) {
        try {
            return context.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null; // argument not typed
        }
    }
}
