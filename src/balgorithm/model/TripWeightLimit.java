package balgorithm.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TripWeightLimit {
    private final String userId;
    private final String tripId;
    private final double weightLimit;

    public TripWeightLimit(String userId, String tripId, double weightLimit) {
        this.userId = userId;
        this.tripId = tripId;
        this.weightLimit = weightLimit;
    }

    public String getUserId() { return userId; }
    public String getTripId() { return tripId; }
    public double getWeightLimit() { return weightLimit; }

    public String toFileLine() {
        return encode(userId) + "\t" + encode(tripId) + "\t" + encode(Double.toString(weightLimit));
    }

    public static TripWeightLimit fromFileLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 3) throw new IllegalArgumentException("Invalid trip weight limit row");
        return new TripWeightLimit(decode(fields[0]), decode(fields[1]),
                Double.parseDouble(decode(fields[2])));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
