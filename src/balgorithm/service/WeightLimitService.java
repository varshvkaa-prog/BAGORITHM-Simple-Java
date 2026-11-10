package balgorithm.service;

import balgorithm.model.Trip;
import balgorithm.model.TripWeightLimit;
import balgorithm.storage.FileStorage;

import java.io.IOException;
import java.util.List;

public class WeightLimitService {
    private final FileStorage storage;
    private final TripService tripService;

    public WeightLimitService(FileStorage storage, TripService tripService) {
        this.storage = storage;
        this.tripService = tripService;
    }

    public Double getForTrip(String tripId, String userId) throws IOException {
        tripService.findForUser(tripId, userId);
        return storage.readWeightLimits().stream()
                .filter(limit -> limit.getUserId().equals(userId) && limit.getTripId().equals(tripId))
                .map(TripWeightLimit::getWeightLimit)
                .findFirst()
                .orElse(null);
    }

    public double saveForTrip(String tripId, String userId, double weightLimit) throws IOException {
        tripService.findForUser(tripId, userId);
        if (!Double.isFinite(weightLimit) || weightLimit < 1 || weightLimit > 100_000) {
            throw new IllegalArgumentException("Luggage limit must be between 1 and 100,000 grams.");
        }
        List<TripWeightLimit> limits = storage.readWeightLimits();
        TripWeightLimit updated = new TripWeightLimit(userId, tripId, weightLimit);
        limits.removeIf(limit -> limit.getUserId().equals(userId) && limit.getTripId().equals(tripId));
        limits.add(updated);
        storage.writeWeightLimits(limits);
        return weightLimit;
    }
}
