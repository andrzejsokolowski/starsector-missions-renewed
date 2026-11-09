package missionsrenewed.tiers;

import java.util.Random;

/** One row of data/config/missions_renewed/tiers.json. Every range is [min, max]. */
public final class Tier {

    public final int level;
    public final float rewardMin, rewardMax;
    public final float fleetPointsMin, fleetPointsMax;
    public final float cargoMin, cargoMax;
    public final float fuelMin, fuelMax;
    public final float crewMin, crewMax;
    public final float stakeMin, stakeMax;
    public final float weight;

    Tier(int level, float[] reward, float[] fleetPoints, float[] cargo, float[] fuel, float[] crew, float[] stake, float weight) {
        this.level = level;
        this.rewardMin = reward[0];
        this.rewardMax = reward[1];
        this.fleetPointsMin = fleetPoints[0];
        this.fleetPointsMax = fleetPoints[1];
        this.cargoMin = cargo[0];
        this.cargoMax = cargo[1];
        this.fuelMin = fuel[0];
        this.fuelMax = fuel[1];
        this.crewMin = crew[0];
        this.crewMax = crew[1];
        this.stakeMin = stake[0];
        this.stakeMax = stake[1];
        this.weight = weight;
    }

    public float rollReward(Random random) {
        return roll(random, rewardMin, rewardMax);
    }

    public float rollFleetPoints(Random random) {
        return roll(random, fleetPointsMin, fleetPointsMax);
    }

    public float rollCargo(Random random) {
        return roll(random, cargoMin, cargoMax);
    }

    public float rollFuel(Random random) {
        return roll(random, fuelMin, fuelMax);
    }

    public float rollCrew(Random random) {
        return roll(random, crewMin, crewMax);
    }

    public float rollStake(Random random) {
        return roll(random, stakeMin, stakeMax);
    }

    private static float roll(Random random, float min, float max) {
        return min + (max - min) * random.nextFloat();
    }
}
