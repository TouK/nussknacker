package pl.touk.nussknacker.ui.definition.editor;

public enum JavaSampleEnum {

    FIRST_VALUE, SECOND_VALUE;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
