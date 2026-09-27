/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.heat;

// How much heat the Solar Thermal Array's receiver takes in, and how hot it gets, from the sun's height,
// the weather, the trough's tracking axis, the biome and how many collectors see the sky. No world access,
// so it can be tested on its own.
//
//   sun       = sin(pi * dayTime / 12000) by day (0 sunrise, 6000 noon, 12000 sunset), 0 at night
//   weather   = thunder ? thunderMult : rain or snow ? rainMult : 1
//   intensity = hasSky ? sun * weather * axisMult * biomeMult : 0
//   HU/t      = round(peakHeat * intensity * skyCount / 4)
//   tempC     = skyCount > 0 ? 20 + (maxTemp - 20) * min(1, intensity) : 20
//
// The trough stows face down at night and in thunderstorms.
public final class SolarModel {
    public static final int DAY_LENGTH = 24_000;
    public static final int DAYLIGHT = 12_000;
    public static final int COLLECTORS = 4;

    private SolarModel() {}

    public record Settings(int peakHeat, int maxTemperature, double rainMultiplier, double thunderMultiplier,
            double northSouthMultiplier, double eastWestMultiplier) {}

    // weather: 0 clear, 1 rain, 2 snow, 3 thunder (see Weather). biomeMultiplier: 1 in a temperate biome.
    public record Conditions(long dayTime, Weather weather, double biomeMultiplier, boolean northSouth, int skyCount, boolean hasSky) {}

    public record Result(double intensity, int heatPerTick, int temperature, boolean stowed) {}

    public enum Weather {
        CLEAR, RAIN, SNOW, THUNDER;

        public static Weather byId(int id) {
            Weather[] values = values();
            return id >= 0 && id < values.length ? values[id] : CLEAR;
        }
    }

    // The sun's height over the trough: 0 at sunrise, 1 at noon, 0 again at sunset and all night.
    public static double sun(long dayTime) {
        long time = Math.floorMod(dayTime, (long) DAY_LENGTH);
        return time < DAYLIGHT ? Math.sin(Math.PI * time / DAYLIGHT) : 0.0;
    }

    public static boolean isNight(long dayTime) {
        return Math.floorMod(dayTime, (long) DAY_LENGTH) >= DAYLIGHT;
    }

    public static double weatherMultiplier(Weather weather, Settings settings) {
        return switch (weather) {
            case CLEAR -> 1.0;
            case RAIN, SNOW -> settings.rainMultiplier();
            case THUNDER -> settings.thunderMultiplier();
        };
    }

    public static Result compute(Conditions conditions, Settings settings) {
        double axis = conditions.northSouth() ? settings.northSouthMultiplier() : settings.eastWestMultiplier();
        double intensity = conditions.hasSky()
                ? sun(conditions.dayTime()) * weatherMultiplier(conditions.weather(), settings) * axis * conditions.biomeMultiplier()
                : 0.0;
        int sky = Math.max(0, Math.min(COLLECTORS, conditions.skyCount()));
        int heat = (int) Math.round(settings.peakHeat() * intensity * sky / COLLECTORS);
        int temperature = sky > 0
                ? (int) Math.round(HeatBuffer.AMBIENT_CELSIUS + (settings.maxTemperature() - HeatBuffer.AMBIENT_CELSIUS) * Math.min(1.0, intensity))
                : HeatBuffer.AMBIENT_CELSIUS;
        boolean stowed = isNight(conditions.dayTime()) || conditions.weather() == Weather.THUNDER;
        return new Result(intensity, heat, temperature, stowed);
    }
}
