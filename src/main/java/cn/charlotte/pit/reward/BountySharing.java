package cn.charlotte.pit.reward;

/** Golden handcuffs redistribute a growth receipt; the sum can never exceed its existing budget. */
public final class BountySharing {
    private BountySharing() {}

    public static int[] distribute(int growth, int[] capacity, int seed) {
        int[] shares = new int[capacity.length];
        if (growth <= 0 || capacity.length == 0) return shares;
        int remaining = (int) ((long) growth * 4 / 5);
        int start = Math.floorMod(seed, capacity.length);
        while (remaining > 0) {
            boolean assigned = false;
            for (int i = 0; i < capacity.length && remaining > 0; i++) {
                int index = (start + i) % capacity.length;
                if (shares[index] >= Math.max(0, capacity[index])) continue;
                shares[index]++;
                remaining--;
                assigned = true;
            }
            if (!assigned) break;
        }
        return shares;
    }
}
