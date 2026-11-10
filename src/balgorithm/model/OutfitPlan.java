package balgorithm.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class OutfitPlan {
    private final String id;
    private final String userId;
    private final String tripId;
    private final String destination;
    private final String weather;
    private final List<OutfitDay> days;

    public OutfitPlan(String id, String userId, String tripId, String destination, String weather,
                      List<OutfitDay> days) {
        this.id = id;
        this.userId = userId;
        this.tripId = tripId;
        this.destination = destination;
        this.weather = weather;
        this.days = List.copyOf(days);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTripId() { return tripId; }
    public String getDestination() { return destination; }
    public String getWeather() { return weather; }
    public List<OutfitDay> getDays() { return days; }

    public String toFileLine() {
        List<String> fields = new ArrayList<>();
        fields.add(encode(id));
        fields.add(encode(userId));
        fields.add(encode(tripId));
        fields.add(encode(destination));
        fields.add(encode(weather));
        fields.add(encode(Integer.toString(days.size())));
        for (OutfitDay day : days) {
            fields.add(encode(Integer.toString(day.getDayNumber())));
            fields.add(encode(day.getActivity()));
            fields.add(encode(Integer.toString(day.getItems().size())));
            for (OutfitItem item : day.getItems()) {
                fields.add(encode(item.getCategory()));
                fields.add(encode(item.getName()));
                fields.add(encode(Double.toString(item.getWeight())));
            }
        }
        return String.join("\t", fields);
    }

    public static OutfitPlan fromFileLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length < 6) throw new IllegalArgumentException("Invalid outfit plan row");
        int dayCount = Integer.parseInt(decode(fields[5]));
        if (dayCount < 1 || dayCount > 365) throw new IllegalArgumentException("Invalid outfit plan day count");
        int fieldIndex = 6;
        List<OutfitDay> days = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < dayCount; dayIndex++) {
            int dayNumber = Integer.parseInt(decode(fields[fieldIndex++]));
            String activity = decode(fields[fieldIndex++]);
            int itemCount = Integer.parseInt(decode(fields[fieldIndex++]));
            if (itemCount < 1 || itemCount > 34 || fieldIndex + itemCount * 3 > fields.length) {
                throw new IllegalArgumentException("Invalid outfit plan items");
            }
            List<OutfitItem> items = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                items.add(new OutfitItem(decode(fields[fieldIndex++]), decode(fields[fieldIndex++]),
                        Double.parseDouble(decode(fields[fieldIndex++]))));
            }
            days.add(new OutfitDay(dayNumber, activity, items));
        }
        if (fieldIndex != fields.length) throw new IllegalArgumentException("Invalid outfit plan fields");
        return new OutfitPlan(decode(fields[0]), decode(fields[1]), decode(fields[2]),
                decode(fields[3]), decode(fields[4]), days);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public static class OutfitDay {
        private final int dayNumber;
        private final String activity;
        private final List<OutfitItem> items;

        public OutfitDay(int dayNumber, String activity, List<OutfitItem> items) {
            this.dayNumber = dayNumber;
            this.activity = activity;
            this.items = List.copyOf(items);
        }

        public int getDayNumber() { return dayNumber; }
        public String getActivity() { return activity; }
        public List<OutfitItem> getItems() { return items; }
        public double getTotalWeight() {
            return items.stream().mapToDouble(OutfitItem::getWeight).sum();
        }
    }

    public static class OutfitItem {
        private final String category;
        private final String name;
        private final double weight;

        public OutfitItem(String category, String name, double weight) {
            this.category = category;
            this.name = name;
            this.weight = weight;
        }

        public String getCategory() { return category; }
        public String getName() { return name; }
        public double getWeight() { return weight; }
    }
}
