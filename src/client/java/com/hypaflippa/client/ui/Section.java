package com.hypaflippa.client.ui;

import java.util.List;

/** A group of tabs with its own button in the window's top bar, e.g. "Flippa" or "Gamba". */
public record Section(String id, String title, List<Tab> tabs) {}
