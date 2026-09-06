package dev.maboullaite.fhemni.catalog;

import java.util.List;

public record CatalogPage(
        List<CatalogVideo> items,
        int page,
        int size,
        long totalElements) {

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
