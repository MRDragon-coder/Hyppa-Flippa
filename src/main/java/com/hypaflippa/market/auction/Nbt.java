package com.hypaflippa.market.auction;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A minimal reader for Minecraft's NBT format, enough to decode the gzipped {@code item_bytes}
 * of Hypixel auctions. Compounds become {@code Map<String, Object>}, lists become
 * {@code List<Object>}, numbers their boxed Java type, strings {@code String}.
 *
 * Self-contained (no Minecraft classes) so it can't break between Minecraft versions and can be
 * tested outside the game.
 */
public final class Nbt {
    private Nbt() {}

    /** Reads a gzipped NBT document; returns its root compound (or null if empty). */
    public static Map<String, Object> readCompressed(byte[] gzipped) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new ByteArrayInputStream(gzipped))))) {
            byte type = in.readByte();
            if (type != 10) return null;
            in.readUTF(); // root name, always "" here
            return compound(in);
        }
    }

    private static Object payload(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                yield bytes;
            }
            // NBT strings use Java's "modified UTF-8" with a 2-byte length: exactly readUTF.
            case 8 -> in.readUTF();
            case 9 -> {
                byte elementType = in.readByte();
                int length = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, length));
                // An empty list may be declared with element type 0 ("end"); it has no payload.
                if (elementType != 0) {
                    for (int i = 0; i < length; i++) list.add(payload(in, elementType));
                }
                yield list;
            }
            case 10 -> compound(in);
            case 11 -> {
                int[] ints = new int[in.readInt()];
                for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
                yield ints;
            }
            case 12 -> {
                long[] longs = new long[in.readInt()];
                for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
                yield longs;
            }
            default -> throw new IOException("Unknown NBT tag type " + type);
        };
    }

    private static Map<String, Object> compound(DataInputStream in) throws IOException {
        Map<String, Object> map = new HashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) return map;
            String name = in.readUTF(); // the name comes before the value
            map.put(name, payload(in, type));
        }
    }

    // --- Small typed accessors for walking the decoded tree safely.

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getCompound(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Map<?, ?> value ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof List<?> value ? (List<Object>) value : null;
    }

    public static String getString(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof String value ? value : null;
    }

    public static int getInt(Map<String, Object> map, String key, int fallback) {
        return map != null && map.get(key) instanceof Number value ? value.intValue() : fallback;
    }
}
