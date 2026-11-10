package balgorithm.service;

import balgorithm.model.PackingItem;
import balgorithm.storage.FileStorage;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ItemService {
    private static final Set<String> CATEGORIES = Set.of("PACKING", "CLOTHING", "SHOPPING");
    private final FileStorage storage;
    private final TripService tripService;

    public ItemService(FileStorage storage, TripService tripService) {
        this.storage = storage;
        this.tripService = tripService;
    }

    public List<PackingItem> listForTrip(String tripId, String userId, String search) throws IOException {
        tripService.findForUser(tripId, userId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return storage.readItems().stream()
                .filter(item -> item.getTripId().equals(tripId) && item.getUserId().equals(userId))
                .filter(item -> query.isEmpty() || item.getName().toLowerCase(Locale.ROOT).contains(query)
                        || item.getCategory().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    public PackingItem create(String userId, String tripId, String name, String category,
                              int quantity, double weight, double price, boolean packed,
                              boolean purchased) throws IOException {
        tripService.findForUser(tripId, userId);
        PackingItem item = validatedItem(UUID.randomUUID().toString(), userId, tripId, name, category,
                quantity, weight, price, packed, purchased);
        List<PackingItem> items = storage.readItems();
        items.add(item);
        storage.writeItems(items);
        return item;
    }

    public PackingItem update(String itemId, String userId, String tripId, String name, String category,
                              int quantity, double weight, double price, boolean packed,
                              boolean purchased) throws IOException {
        PackingItem existing = findForTrip(itemId, userId, tripId);
        PackingItem updated = validatedItem(existing.getId(), userId, tripId, name, category,
                quantity, weight, price, packed, purchased);
        List<PackingItem> items = storage.readItems();
        items.replaceAll(item -> item.getId().equals(itemId) ? updated : item);
        storage.writeItems(items);
        return updated;
    }

    public void delete(String itemId, String userId, String tripId) throws IOException {
        findForTrip(itemId, userId, tripId);
        storage.writeItems(storage.readItems().stream().filter(item -> !item.getId().equals(itemId)).toList());
    }

    private PackingItem findForTrip(String itemId, String userId, String tripId) throws IOException {
        return listForTrip(tripId, userId, "").stream().filter(item -> item.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Packing item not found."));
    }

    private PackingItem validatedItem(String id, String userId, String tripId, String name, String category,
                                      int quantity, double weight, double price, boolean packed,
                                      boolean purchased) {
        String cleanName = name == null ? "" : name.trim();
        String cleanCategory = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (cleanName.isBlank() || cleanName.length() > 100) {
            throw new IllegalArgumentException("Enter an item name between 1 and 100 characters.");
        }
        if (!CATEGORIES.contains(cleanCategory)) throw new IllegalArgumentException("Choose a valid item category.");
        if (quantity < 1 || quantity > 999) throw new IllegalArgumentException("Quantity must be between 1 and 999.");
        if (!Double.isFinite(weight) || weight < 0 || weight > 100_000) {
            throw new IllegalArgumentException("Weight must be a non-negative number of grams.");
        }
        if (!Double.isFinite(price) || price < 0 || price > 1_000_000_000) {
            throw new IllegalArgumentException("Price must be a non-negative amount.");
        }
        return new PackingItem(id, userId, tripId, cleanName, cleanCategory, quantity, weight, price,
                packed, purchased);
    }
}
