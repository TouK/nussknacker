export function inferDisplayType(val: unknown): string {
    if (val === null || val === undefined) return "null";
    if (typeof val === "boolean") return "Boolean";
    if (typeof val === "number") return Number.isInteger(val) ? "Integer" : "Float";
    if (typeof val === "string") return "String";
    if (Array.isArray(val)) return `Array[${val.length > 0 ? inferDisplayType(val[0]) : "?"}]`;
    if (typeof val === "object") return "Map";
    return "?";
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function typingResultToSample(t: any): unknown {
    if (!t) return null;
    // Fields present → it's a record type (TypedObjectTypingResult), regardless of type discriminator
    if (t.fields && typeof t.fields === "object" && Object.keys(t.fields).length > 0) {
        return Object.fromEntries(Object.entries(t.fields as Record<string, unknown>).map(([k, v]) => [k, typingResultToSample(v)]));
    }
    const name: string = t.refClazzName ?? "";
    if (name.includes("List") || name.includes("Collection")) {
        const elem = t.params?.[0];
        return elem ? [typingResultToSample(elem)] : [];
    }
    if (name === "java.lang.String") return "";
    if (name === "java.lang.Boolean") return false;
    if (name.includes("Integer") || name.includes("Long") || name.includes("Short")) return 0;
    if (name.includes("Double") || name.includes("Float")) return 0.5;
    if (name.includes("Map")) return {};
    return null;
}

/** Convert a dragged path expression to null-safe SpEL: #a.b.c → #a?.b?.c */
export function toNullSafe(spelExpr: string): string {
    if (!spelExpr.startsWith("#")) return spelExpr;
    return "#" + spelExpr.slice(1).replace(/(?<!\?)\./g, "?.");
}

export function treeNodeMatchesFilter(name: string, value: unknown, filterText: string): boolean {
    if (name.toLowerCase().includes(filterText)) return true;
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
        return Object.entries(value as Record<string, unknown>).some(([k, v]) => treeNodeMatchesFilter(k, v, filterText));
    }
    return false;
}
