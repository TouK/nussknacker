package pl.touk.sample;

public enum JavaSampleEnum {

    FIRST_VALUE, SECOND_VALUE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
