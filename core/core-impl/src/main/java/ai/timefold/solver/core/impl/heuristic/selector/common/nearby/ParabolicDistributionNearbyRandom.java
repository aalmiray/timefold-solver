package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

/**
 * {@code P(x) = 3(m - x)²/m³}.
 * <p>
 * Cumulative probability: {@code F(x) = 1 - (1 - x/m)³}.
 * <p>
 * Inverse cumulative probability: {@code F(p) = m(1 - (1 - p)^(1/3))}.
 */
public final class ParabolicDistributionNearbyRandom implements NearbyRandom {
    private final int sizeMaximum;

    public ParabolicDistributionNearbyRandom(int sizeMaximum) {
        this.sizeMaximum = sizeMaximum;
        if (sizeMaximum < 1) {
            throw new IllegalArgumentException("The maximum (" + sizeMaximum
                    + ") must be at least 1.");
        }
    }

    @Override
    public int nextInt(Random random, int nearbySize) {
        int m = sizeMaximum <= nearbySize ? sizeMaximum : nearbySize;
        double p = random.nextDouble();
        double x = m * (1.0 - Math.pow(1.0 - p, 1.0 / 3.0));
        int next = (int) x;
        // Due to a rounding error it might return m
        if (next >= m) {
            next = m - 1;
        }
        return next;
    }

    @Override
    public int getOverallSizeMaximum() {
        return sizeMaximum;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        ParabolicDistributionNearbyRandom that = (ParabolicDistributionNearbyRandom) other;
        return sizeMaximum == that.sizeMaximum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeMaximum);
    }

}
