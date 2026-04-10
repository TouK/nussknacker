export const TYPE_COLORS: Record<string, string> = {
    String: "#7ec87e",
    Float: "#6ea8c8",
    Double: "#6ea8c8",
    Int: "#c8956e",
    Integer: "#c8956e",
    Long: "#c8956e",
    Bool: "#e0c84a",
    Boolean: "#e0c84a",
};

export function getTypeColor(type: string): string {
    return TYPE_COLORS[type] ?? "#a0a8b8";
}
