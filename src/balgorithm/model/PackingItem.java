package balgorithm.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PackingItem {
    private final String id;
    private final String userId;
    private final String tripId;
    private final String name;
    private final String category;
    private final int quantity;
    private final double weight;
    private final double price;
    private final boolean packed;
    private final boolean purchased;

    public PackingItem(String id, String userId, String tripId, String name, String category,
                       int quantity, double weight, double price, boolean packed, boolean purchased) {
        this.id = id;
        this.userId = userId;
        this.tripId = tripId;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.weight = weight;
        this.price = price;
        this.packed = packed;
        this.purchased = purchased;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTripId() { return tripId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public double getWeight() { return weight; }
    public double getPrice() { return price; }
    public boolean isPacked() { return packed; }
    public boolean isPurchased() { return purchased; }
    public double getTotalWeight() { return quantity * weight; }
    public double getTotalPrice() { return quantity * price; }

    public String toFileLine() {
        return encode(id) + "\t" + encode(userId) + "\t" + encode(tripId) + "\t" + encode(name) + "\t"
                + encode(category) + "\t" + encode(Integer.toString(quantity)) + "\t"
                + encode(Double.toString(weight)) + "\t" + encode(Double.toString(price)) + "\t"
                + encode(Boolean.toString(packed)) + "\t" + encode(Boolean.toString(purchased));
    }

    public static PackingItem fromFileLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 8 && fields.length != 9 && fields.length != 10) {
            throw new IllegalArgumentException("Invalid item row");
        }
        boolean hasPrice = fields.length == 10;
        double price = hasPrice ? Double.parseDouble(decode(fields[7])) : 0;
        int packedIndex = hasPrice ? 8 : 7;
        int purchasedIndex = fields.length >= 9 ? packedIndex + 1 : -1;
        String category = decode(fields[4]);
        if ("INVENTORY".equals(category)) category = "PACKING";
        return new PackingItem(decode(fields[0]), decode(fields[1]), decode(fields[2]), decode(fields[3]),
                category, Integer.parseInt(decode(fields[5])), Double.parseDouble(decode(fields[6])),
                price, Boolean.parseBoolean(decode(fields[packedIndex])),
                purchasedIndex >= 0 && Boolean.parseBoolean(decode(fields[purchasedIndex])));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
