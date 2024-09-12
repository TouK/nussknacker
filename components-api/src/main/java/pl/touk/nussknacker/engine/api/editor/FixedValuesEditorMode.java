package pl.touk.nussknacker.engine.api.editor;

public enum FixedValuesEditorMode {
    LIST, RADIO;

    public static FixedValuesEditorMode fromName(String name) {
        switch (name) {
            case "LIST":
                return LIST;
            case "RADIO":
            default:
                return RADIO;
        }
    }
}
