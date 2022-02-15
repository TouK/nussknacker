package pl.touk.nussknacker.engine.api.editor;

public enum DualEditorMode {
    RAW, SIMPLE;

    public static DualEditorMode fromName(String name) {
        switch (name) {
            case "SIMPLE":
                return SIMPLE;
            case "RAW":
            default:
                return RAW;
        }
    }
}
