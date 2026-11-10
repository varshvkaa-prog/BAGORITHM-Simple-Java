package balgorithm.service;

import balgorithm.model.OutfitPlan;
import balgorithm.model.OutfitPlan.OutfitDay;
import balgorithm.model.OutfitPlan.OutfitItem;
import balgorithm.model.PackingItem;
import balgorithm.storage.FileStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class OutfitPlanService {
    private static final Set<String> WEATHER_OPTIONS =
            Set.of("Sunny", "Hot", "Moderate", "Cold", "Rainy", "Humid", "Snowy");
    private static final Set<String> ACTIVITY_OPTIONS =
            Set.of("Sightseeing", "Trekking", "Beach", "Swimming", "Camping", "Business", "Casual");
    private static final List<String> ITEM_CATEGORIES = List.of("Top", "Bottom", "Footwear", "Accessory");

    private final FileStorage storage;
    private final TripService tripService;
    private final ItemService itemService;

    public OutfitPlanService(FileStorage storage, TripService tripService, ItemService itemService) {
        this.storage = storage;
        this.tripService = tripService;
        this.itemService = itemService;
    }

    public OutfitPlan generate(String userId, String tripId, String destination, String weather,
                               List<String> activities) throws IOException {
        return generatePlan(userId, tripId, destination, weather, activities, List.of(), false);
    }

    public OutfitPlan generate(String userId, String tripId, String destination, String weather,
                               List<String> activities, List<String> selectedItemIds) throws IOException {
        return generatePlan(userId, tripId, destination, weather, activities, selectedItemIds, true);
    }

    private OutfitPlan generatePlan(String userId, String tripId, String destination, String weather,
                                    List<String> activities, List<String> selectedItemIds,
                                    boolean requireSelectedItems) throws IOException {
        tripService.findForUser(tripId, userId);
        String cleanDestination = destination == null ? "" : destination.trim();
        if (cleanDestination.isBlank() || cleanDestination.length() > 100) {
            throw new IllegalArgumentException("Enter a destination between 1 and 100 characters.");
        }
        String selectedWeather = selectOption(weather, WEATHER_OPTIONS, "weather");
        if (activities == null || activities.isEmpty() || activities.size() > 365) {
            throw new IllegalArgumentException("Choose between 1 and 365 travel days.");
        }
        List<String> requestedItemIds = selectedItemIds == null ? List.of() : selectedItemIds;
        if ((requireSelectedItems && requestedItemIds.isEmpty()) || requestedItemIds.size() > 30) {
            throw new IllegalArgumentException("Select between 1 and 30 clothing items for your outfit plan.");
        }
        if (requestedItemIds.stream().distinct().count() != requestedItemIds.size()) {
            throw new IllegalArgumentException("The selected clothing list contains duplicate items.");
        }

        List<PackingItem> selectedItems = itemService.listForTrip(tripId, userId, "").stream()
                .filter(item -> requestedItemIds.contains(item.getId()))
                .toList();
        if (selectedItems.size() != requestedItemIds.size()
                || selectedItems.stream().anyMatch(item -> !"CLOTHING".equals(item.getCategory()))) {
            throw new IllegalArgumentException("Choose clothing items from this trip's clothing list.");
        }

        List<OutfitDay> days = new ArrayList<>();
        for (int index = 0; index < activities.size(); index++) {
            String activity = selectOption(activities.get(index), ACTIVITY_OPTIONS, "activity for day " + (index + 1));
            OutfitDay suggestedDay = createDay(index + 1, activity, cleanDestination, selectedWeather);
            List<OutfitItem> dayItems = new ArrayList<>(suggestedDay.getItems());
            for (PackingItem item : selectedItems) {
                dayItems.add(new OutfitItem("Selected clothing", item.getName(), item.getTotalWeight()));
            }
            days.add(new OutfitDay(index + 1, activity, dayItems));
        }
        return new OutfitPlan(UUID.randomUUID().toString(), userId, tripId, cleanDestination,
                selectedWeather, days);
    }

    public OutfitPlan save(String userId, String tripId, String destination, String weather,
                           List<OutfitDay> days) throws IOException {
        tripService.findForUser(tripId, userId);
        OutfitPlan validated = validatePlan(userId, tripId, destination, weather, days);
        OutfitPlan saved = new OutfitPlan(UUID.randomUUID().toString(), userId, tripId,
                validated.getDestination(), validated.getWeather(), validated.getDays());
        List<OutfitPlan> plans = storage.readOutfitPlans();
        plans.add(saved);
        storage.writeOutfitPlans(plans);
        return saved;
    }

    public List<OutfitPlan> listForTrip(String tripId, String userId) throws IOException {
        tripService.findForUser(tripId, userId);
        return storage.readOutfitPlans().stream()
                .filter(plan -> plan.getUserId().equals(userId) && plan.getTripId().equals(tripId))
                .toList();
    }

    public OutfitPlan findForTrip(String planId, String tripId, String userId) throws IOException {
        return listForTrip(tripId, userId).stream().filter(plan -> plan.getId().equals(planId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Saved outfit plan not found."));
    }

    private OutfitPlan validatePlan(String userId, String tripId, String destination, String weather,
                                    List<OutfitDay> days) {
        String cleanDestination = destination == null ? "" : destination.trim();
        if (cleanDestination.isBlank() || cleanDestination.length() > 100) {
            throw new IllegalArgumentException("Enter a destination between 1 and 100 characters.");
        }
        String selectedWeather = selectOption(weather, WEATHER_OPTIONS, "weather");
        if (days == null || days.isEmpty() || days.size() > 365) {
            throw new IllegalArgumentException("Choose between 1 and 365 travel days.");
        }
        List<OutfitDay> cleanDays = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            OutfitDay day = days.get(dayIndex);
            if (day == null || day.getDayNumber() != dayIndex + 1) {
                throw new IllegalArgumentException("Outfit days must be numbered consecutively from day 1.");
            }
            String activity = selectOption(day.getActivity(), ACTIVITY_OPTIONS, "activity for day " + (dayIndex + 1));
            if (day.getItems().size() < ITEM_CATEGORIES.size()
                    || day.getItems().size() > ITEM_CATEGORIES.size() + 30) {
                throw new IllegalArgumentException("Each outfit needs a top, bottom, footwear, accessory and selected clothing.");
            }
            List<OutfitItem> cleanItems = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < day.getItems().size(); itemIndex++) {
                OutfitItem item = day.getItems().get(itemIndex);
                String expectedCategory = itemIndex < ITEM_CATEGORIES.size()
                        ? ITEM_CATEGORIES.get(itemIndex) : "Selected clothing";
                if (item == null || !expectedCategory.equals(item.getCategory())
                        || item.getName() == null || item.getName().isBlank() || item.getName().length() > 100
                        || !Double.isFinite(item.getWeight()) || item.getWeight() < 0 || item.getWeight() > 100_000) {
                    throw new IllegalArgumentException("Each outfit item needs a valid category, name and weight.");
                }
                cleanItems.add(new OutfitItem(expectedCategory, item.getName().trim(), item.getWeight()));
            }
            cleanDays.add(new OutfitDay(dayIndex + 1, activity, cleanItems));
        }
        return new OutfitPlan("", userId, tripId, cleanDestination, selectedWeather, cleanDays);
    }

    private OutfitDay createDay(int dayNumber, String activity, String destination, String weather) {
        boolean coastal = containsAny(destination, "kerala", "kochi", "goa", "mumbai", "chennai");
        boolean highland = containsAny(destination, "ooty", "udhagamandalam", "manali");
        String top = chooseTop(weather, coastal, dayNumber);
        String bottom = chooseBottom(activity, dayNumber);
        String footwear = chooseFootwear(activity, weather, coastal);
        String accessory = chooseAccessory(activity, weather, destination, coastal, highland);

        List<OutfitItem> items = List.of(
                new OutfitItem("Top", top, topWeight(weather)),
                new OutfitItem("Bottom", bottom, bottomWeight(activity)),
                new OutfitItem("Footwear", footwear, footwearWeight(activity, weather)),
                new OutfitItem("Accessory", accessory, accessoryWeight(activity, weather)));
        return new OutfitDay(dayNumber, activity, items);
    }

    private String chooseTop(String weather, boolean coastal, int dayNumber) {
        int variation = (dayNumber - 1) % 3;
        return switch (weather) {
            case "Cold" -> List.of("Thermal long-sleeve top", "Warm merino sweater", "Fleece pullover").get(variation);
            case "Snowy" -> List.of("Thermal base-layer top", "Insulated fleece layer", "Warm wool sweater").get(variation);
            case "Moderate" -> List.of("Comfortable everyday shirt", "Soft travel top with light layer",
                    "Breathable everyday top").get(variation);
            case "Rainy" -> List.of("Quick-dry long-sleeve shirt", "Light rain-ready top", "Water-resistant travel top").get(variation);
            case "Humid" -> List.of("Moisture-wicking shirt", "Breathable cotton shirt", "Light linen shirt").get(variation);
            case "Hot" -> List.of("Light cotton shirt", "Linen short-sleeve top", "Loose breathable T-shirt").get(variation);
            default -> coastal
                    ? List.of("Light cotton T-shirt", "Breathable coastal shirt", "Loose linen top").get(variation)
                    : List.of("Cotton T-shirt", "Comfortable travel shirt", "Soft casual top").get(variation);
        };
    }

    private String chooseBottom(String activity, int dayNumber) {
        int variation = (dayNumber - 1) % 3;
        return switch (activity) {
            case "Beach" -> List.of("Lightweight beach shorts", "Easy-dry board shorts", "Linen beach trousers").get(variation);
            case "Swimming" -> List.of("Quick-dry swim shorts", "Swim leggings", "Chlorine-resistant swim shorts").get(variation);
            case "Trekking" -> List.of("Stretch hiking trousers", "Lightweight trail pants", "Trekking cargo trousers").get(variation);
            case "Camping" -> List.of("Durable outdoor trousers", "Convertible camping pants", "Rugged camp trousers").get(variation);
            case "Business" -> List.of("Smart tailored trousers", "Wrinkle-resistant dress trousers", "Classic work slacks").get(variation);
            case "Sightseeing" -> List.of("Comfortable walking trousers", "Versatile travel trousers", "Lightweight sightseeing pants").get(variation);
            default -> List.of("Casual jeans", "Soft everyday trousers", "Relaxed-fit travel pants").get(variation);
        };
    }

    private String chooseFootwear(String activity, String weather, boolean coastal) {
        if (weather.equals("Snowy")) return "Insulated waterproof boots";
        if (weather.equals("Cold")) return "Warm walking boots";
        return switch (activity) {
            case "Trekking" -> "Supportive trekking boots";
            case "Camping" -> "Sturdy camping boots";
            case "Beach" -> "Lightweight beach sandals";
            case "Swimming" -> "Non-slip water shoes";
            case "Business" -> "Comfortable smart shoes";
            case "Casual" -> "Casual everyday sneakers";
            default -> coastal ? "Walking sandals" : "Comfortable sightseeing shoes";
        };
    }

    private String chooseAccessory(String activity, String weather, String destination,
                                   boolean coastal, boolean highland) {
        String accessory;
        if (weather.equals("Snowy")) accessory = "Warm hat and gloves";
        else if (weather.equals("Cold")) accessory = "Light scarf and warm socks";
        else if (weather.equals("Rainy")) accessory = "Compact rain shell";
        else if (weather.equals("Moderate") && (activity.equals("Trekking") || activity.equals("Camping")
                || highland)) accessory = "Light packable jacket";
        else if (activity.equals("Beach") || activity.equals("Swimming")) accessory = "Sun hat and sunglasses";
        else if (activity.equals("Trekking") || activity.equals("Camping")) accessory = "Lightweight outdoor cap";
        else if (weather.equals("Sunny") || weather.equals("Hot")) accessory = "Sunglasses and sun hat";
        else if (highland) accessory = "Light evening layer";
        else if (coastal) accessory = "Quick-dry layer";
        else accessory = "Reusable light layer";
        String cleanDestination = destination.trim();
        if (cleanDestination.length() > 70) cleanDestination = cleanDestination.substring(0, 70).trim();
        return accessory + " for " + cleanDestination;
    }

    private double topWeight(String weather) {
        return switch (weather) {
            case "Cold", "Snowy" -> 420;
            case "Moderate" -> 210;
            case "Rainy" -> 220;
            default -> 160;
        };
    }

    private double bottomWeight(String activity) {
        return switch (activity) {
            case "Beach", "Swimming" -> 180;
            case "Trekking", "Camping" -> 380;
            case "Business" -> 340;
            default -> 300;
        };
    }

    private double footwearWeight(String activity, String weather) {
        if (weather.equals("Snowy")) return 900;
        if (weather.equals("Cold")) return 700;
        return switch (activity) {
            case "Trekking", "Camping" -> 750;
            case "Beach", "Swimming" -> 260;
            case "Business" -> 600;
            default -> 420;
        };
    }

    private double accessoryWeight(String activity, String weather) {
        if (weather.equals("Snowy")) return 160;
        if (weather.equals("Cold")) return 140;
        if (weather.equals("Rainy")) return 240;
        if (activity.equals("Beach") || activity.equals("Swimming")
                || weather.equals("Sunny") || weather.equals("Hot")) return 100;
        return 80;
    }

    private boolean containsAny(String destination, String... names) {
        String normalized = destination.trim().toLowerCase(Locale.ROOT);
        for (String name : names) {
            if (normalized.contains(name)) return true;
        }
        return false;
    }

    private String selectOption(String selection, Set<String> options, String fieldName) {
        if (selection != null) {
            String cleanSelection = selection.trim();
            for (String option : options) {
                if (option.equalsIgnoreCase(cleanSelection)) return option;
            }
        }
        throw new IllegalArgumentException("Choose a valid " + fieldName + " option.");
    }
}
