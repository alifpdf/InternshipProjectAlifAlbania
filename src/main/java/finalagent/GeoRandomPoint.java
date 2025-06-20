package finalagent;

import java.util.Random;

public class GeoRandomPoint {

    private static final double EARTH_RADIUS = 6371000; // en mtres
    private static final Random random = new Random();

    // Haversine  distance entre deux points (en mtres)
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(rLat1) * Math.cos(rLat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    //  Mthode simplifie (plate) pour petits rayons
    public static double[] generateFlatRandomPoint(double centerLat, double centerLon, double radiusMeters) {
        double angle = 2 * Math.PI * random.nextDouble();
        double distance = radiusMeters * Math.sqrt(random.nextDouble());

        double deltaLat = distance * Math.cos(angle) / 111000.0;
        double deltaLon = distance * Math.sin(angle) / (111000.0 * Math.cos(Math.toRadians(centerLat)));

        double newLat = centerLat + deltaLat;
        double newLon = centerLon + deltaLon;

        return new double[]{newLat, newLon};
    }

    // Mthode sphrique pour grands rayons
    public static double[] generateSphericalRandomPoint(double centerLat, double centerLon, double radiusMeters) {
        double distanceRad = (radiusMeters / EARTH_RADIUS) * Math.sqrt(random.nextDouble());
        double angle = 2 * Math.PI * random.nextDouble();

        double centerLatRad = Math.toRadians(centerLat);
        double centerLonRad = Math.toRadians(centerLon);

        double newLatRad = Math.asin(Math.sin(centerLatRad) * Math.cos(distanceRad) +
                Math.cos(centerLatRad) * Math.sin(distanceRad) * Math.cos(angle));

        double newLonRad = centerLonRad + Math.atan2(
                Math.sin(angle) * Math.sin(distanceRad) * Math.cos(centerLatRad),
                Math.cos(distanceRad) - Math.sin(centerLatRad) * Math.sin(newLatRad));

        double newLat = Math.toDegrees(newLatRad);
        double newLon = Math.toDegrees(newLonRad);

        return new double[]{newLat, newLon};
    }

    //  Choix automatique : plate si <1 km, sinon sphrique
    public static double[] generateRandomPointAroundCenter(double centerLat, double centerLon, double radiusMeters) {
        if (radiusMeters <= 1000) {
            return generateFlatRandomPoint(centerLat, centerLon, radiusMeters);
        } else {
            return generateSphericalRandomPoint(centerLat, centerLon, radiusMeters);
        }
    }

    // Test
    public static void main(String[] args) {
        double centerLat = 41.0;
        double centerLon = 21.0;

        double[] radii = {20, 500, 1500, 3000}; // diffrents tests

        for (double radius : radii) {
            double[] point = generateRandomPointAroundCenter(centerLat, centerLon, radius);
            double d = haversine(centerLat, centerLon, point[0], point[1]);

            System.out.printf("\n Test avec rayon %.0f m:\n", radius);
            System.out.printf("   ? Point : [%.8f, %.8f]\n", point[0], point[1]);
            System.out.printf("   ? Distance relle : %.2f m\n", d);
        }
    }
}
