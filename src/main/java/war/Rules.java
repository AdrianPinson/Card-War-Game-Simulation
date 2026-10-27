package war;

/** Last-card exhaustion variant; see docs/rules.md. Round limits count full top-level rounds. */
public record Rules(int faceDownCards, int maxRounds, boolean detectCycles) {
    public static Rules defaults() { return new Rules(3, 10_000, true); }
    public Rules {
        if (faceDownCards < 0 || faceDownCards > 10) throw new IllegalArgumentException("face-down cards must be 0..10");
        if (maxRounds < 1 || maxRounds > 1_000_000) throw new IllegalArgumentException("max rounds must be 1..1000000");
    }
}
