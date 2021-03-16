package pl.touk.nussknacker.engine.flink.util.transformer.aggregate;

public enum TumblingWindowTrigger {
    OnEvent("On each event"),
    OnEnd("After window closes"),
    OnEndWithExtraWindow("After window closes, also when no event for key");

    private final String label;

    TumblingWindowTrigger(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
