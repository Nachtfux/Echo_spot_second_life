package ai.pocket.spotclock;

/** Moon phase from a reference new moon and the mean synodic month. Accurate to well under a day. */
final class Moon {
    /** New moon 2000-01-06 18:14 UTC. */
    private static final long EPOCH_MS = 947182440000L;
    private static final double SYNODIC_DAYS = 29.530588853;

    private Moon() {}

    /** 0 = new moon, 0.25 = first quarter, 0.5 = full moon, 0.75 = last quarter. */
    static double phase(long ms) {
        double days = (ms - EPOCH_MS) / 86400000.0;
        double p = (days / SYNODIC_DAYS) % 1.0;
        return p < 0 ? p + 1 : p;
    }
}
