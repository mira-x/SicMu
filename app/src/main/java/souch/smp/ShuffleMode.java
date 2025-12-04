package souch.smp;

public enum ShuffleMode {
    SEQUENTIAL(0),
    RANDOM(1),
    RADIO(2); // Radio is basically random selection (above) plus random song start time when playing the first song

    public final int num;
    ShuffleMode(int num) {
        this.num = num;
    }

    public static ShuffleMode valueOf(int a) {
        for (ShuffleMode m : ShuffleMode.values()) {
            if (m.num == a) {
                return m;
            }
        }
        throw new IllegalArgumentException("Shuffle mode is invalid");
    }
}
