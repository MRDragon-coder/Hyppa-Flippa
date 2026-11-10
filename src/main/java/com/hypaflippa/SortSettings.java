package com.hypaflippa;

import java.util.ArrayList;
import java.util.List;

/** How a sortable list is ordered and which of its columns are hidden. Part of each list's saved settings. */
public abstract class SortSettings {
    /** The sort mode of the column the list is sorted on ("name" for the name column). */
    public String sortBy;
    public boolean sortAscending = false;
    /** Sort modes of the columns the player hid (the name column can't be hidden). */
    public List<String> hiddenColumns = new ArrayList<>();

    /** The direction a column starts in when first picked. */
    public abstract boolean startsAscending(String sortMode);

    public boolean isColumnHidden(String mode) {
        return hiddenColumns != null && hiddenColumns.contains(mode);
    }

    public void toggleColumn(String mode) {
        if (hiddenColumns == null) hiddenColumns = new ArrayList<>();
        if (!hiddenColumns.remove(mode)) hiddenColumns.add(mode);
    }

    /** A click on a column header: the active column flips its direction, another one becomes active. */
    public void clickSort(String mode) {
        if (mode.equals(sortBy)) {
            sortAscending = !sortAscending;
        } else {
            sortBy = mode;
            sortAscending = startsAscending(mode);
        }
    }
}
