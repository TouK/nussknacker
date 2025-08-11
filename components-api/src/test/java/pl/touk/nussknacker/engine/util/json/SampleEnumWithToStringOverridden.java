package pl.touk.nussknacker.engine.util.json;

public enum SampleEnumWithToStringOverridden {
    LOREM("lorem"), IPSUM("ipsum"), DOLOR("dolor");

    public final String name;

    SampleEnumWithToStringOverridden(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
