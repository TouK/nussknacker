package pl.touk.nussknacker.engine.flink.util.transformer.aggregate;

public enum SessionWindowTrigger {

    OnEvent("On each event"),
    OnEnd("After session end");

    private final String label;

    SessionWindowTrigger(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

}
