package cn.charlotte.pit.reward;

import java.util.Arrays;

/** Split an administrator-issued packet's face value once, including rounding remainders. */
public final class CoinDistribution {
    private CoinDistribution() {}

    public static int[] byDistance(int money, double[] distances) {
        int[] result = new int[distances.length];
        if (money <= 0 || distances.length == 0) return result;
        double totalDistance = 0;
        int valid = 0;
        for (double distance : distances) if (Double.isFinite(distance) && distance >= 0) { totalDistance += distance; valid++; }
        if (valid == 0) return result;
        double[] weights = new double[distances.length];
        double totalWeight = 0;
        for (int i = 0; i < weights.length; i++) {
            if (!Double.isFinite(distances[i]) || distances[i] < 0) continue;
            weights[i] = valid == 1 || totalDistance <= 0 ? 1 : Math.max(0, 1 - distances[i] / totalDistance);
            totalWeight += weights[i];
        }
        int remaining = money;
        for (int i = 0; i < result.length; i++) {
            result[i] = Math.min(remaining, (int) Math.floor(money * weights[i] / totalWeight));
            remaining -= result[i];
        }
        Integer[] order = java.util.stream.IntStream.range(0, distances.length).filter(i -> weights[i] > 0).boxed().toArray(Integer[]::new);
        Arrays.sort(order, java.util.Comparator.comparingDouble(i -> distances[i]));
        if (order.length > 0) {
            int each = remaining / order.length, extra = remaining % order.length;
            for (int i = 0; i < order.length; i++) result[order[i]] += each + (i < extra ? 1 : 0);
        }
        return result;
    }
}
