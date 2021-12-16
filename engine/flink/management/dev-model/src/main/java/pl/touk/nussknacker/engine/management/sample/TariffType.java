package pl.touk.nussknacker.engine.management.sample;

public enum TariffType {

    NORMAL, GOLD;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
