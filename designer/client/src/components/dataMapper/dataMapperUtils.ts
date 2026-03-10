// ─── Types ────────────────────────────────────────────────────────────────────

export type NuType = "String" | "Integer" | "Long" | "Float" | "Double" | "Boolean" | "BigDecimal" | "List" | "Map" | "Any";

export interface MapEntryDef {
    id: number;
    key: string;
    expression: string;
}

export interface FieldDef {
    id: number;
    name: string;
    type: NuType;
    expression: string;
    mapEntries: MapEntryDef[];
    useMapBuilder: boolean;
}

export type ContextData = Record<string, unknown>;

export interface TopicEntry {
    topic: string;
    schema: Record<string, unknown>;
}

// ─── Constants ────────────────────────────────────────────────────────────────

export const NU_TYPES: NuType[] = ["String", "Integer", "Long", "Float", "Double", "Boolean", "BigDecimal", "List", "Map", "Any"];

// ─── ID counter ───────────────────────────────────────────────────────────────

export let _nextId = 1;

/** Consume and return the next unique id. */
export function nextId(): number {
    return _nextId++;
}

// ─── Utilities ────────────────────────────────────────────────────────────────

export function makeField(name = "", type: NuType = "String"): FieldDef {
    return { id: _nextId++, name, type, expression: "", mapEntries: [], useMapBuilder: false };
}

export function makeMapEntry(): MapEntryDef {
    return { id: _nextId++, key: "", expression: "" };
}

export function inferNuType(val: unknown): NuType {
    if (val === null || val === undefined) return "String";
    if (typeof val === "boolean") return "Boolean";
    if (typeof val === "number") {
        if (Number.isInteger(val)) return val > 2147483647 || val < -2147483648 ? "Long" : "Integer";
        return "Double";
    }
    if (typeof val === "string") return "String";
    if (Array.isArray(val)) return "List";
    if (typeof val === "object") return "Map";
    return "Any";
}

export function fieldsFromSample(obj: unknown): Array<Omit<FieldDef, "id">> {
    if (typeof obj !== "object" || obj === null || Array.isArray(obj)) return [];
    return Object.entries(obj as Record<string, unknown>).map(([k, v]) => ({
        name: k,
        type: inferNuType(v),
        expression: "",
        mapEntries: [],
        useMapBuilder: false,
    }));
}

/** Split top-level comma-separated entries respecting nested braces. */
export function splitTopLevel(inner: string): string[] {
    const parts: string[] = [];
    let braces = 0;
    let parens = 0;
    let brackets = 0;
    let start = 0;
    for (let i = 0; i < inner.length; i++) {
        const ch = inner[i];
        if (ch === "{") braces++;
        else if (ch === "}") braces--;
        else if (ch === "(") parens++;
        else if (ch === ")") parens--;
        else if (ch === "[") brackets++;
        else if (ch === "]") brackets--;
        else if (ch === "," && braces === 0 && parens === 0 && brackets === 0) {
            parts.push(inner.slice(start, i).trim());
            start = i + 1;
        }
    }
    parts.push(inner.slice(start).trim());
    return parts.filter(Boolean);
}

/** Parse a SpEL record expression `{ key: expr, ... }` back into FieldDef[]. */
export function parseSpelToFields(expression: string): FieldDef[] | null {
    const trimmed = expression.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null;
    const inner = trimmed.slice(1, -1).trim();
    const parts = splitTopLevel(inner);
    if (parts.length === 0) return [];

    const fields: FieldDef[] = [];
    for (const part of parts) {
        const colonIdx = part.indexOf(":");
        if (colonIdx === -1) continue;
        const name = part.slice(0, colonIdx).trim();
        const val = part.slice(colonIdx + 1).trim();
        if (!name) continue;

        // Nested record → Build Map mode
        if (val.startsWith("{") && val.endsWith("}")) {
            const mapParts = splitTopLevel(val.slice(1, -1).trim());
            const mapEntries: MapEntryDef[] = mapParts
                .map((mp) => {
                    const ci = mp.indexOf(":");
                    if (ci === -1) return null;
                    const k = mp.slice(0, ci).trim();
                    const v = mp.slice(ci + 1).trim();
                    return { id: nextId(), key: k, expression: v };
                })
                .filter((e): e is MapEntryDef => e !== null);
            const f = makeField(name, "Map");
            f.mapEntries = mapEntries;
            f.useMapBuilder = true;
            fields.push(f);
            continue;
        }

        // null → empty field
        if (val === "null") {
            fields.push(makeField(name, "Any"));
            continue;
        }

        // Any SpEL expression (including #path)
        const field = makeField(name, "Any");
        field.expression = val;
        fields.push(field);
    }
    return fields;
}

// ─── App constants ─────────────────────────────────────────────────────────────

export const SAMPLE_CONTEXT: ContextData = {
    input: {},
    http_output: {
        request: {
            headers: [
                { name: "Accept-Encoding", value: "gzip, deflate" },
                { name: "Content-Type", value: "application/json; charset=utf-8" },
            ],
            method: "GET",
            body: {},
            url: "https://opensky-network.org/api/states/all",
        },
        response: {
            headers: [],
            body: {
                time: 1772630871,
                states: [
                    [
                        "3c6447",
                        "",
                        "Germany",
                        1772630844,
                        1772630844,
                        20.9692,
                        52.1707,
                        null,
                        true,
                        3.34,
                        151.88,
                        null,
                        null,
                        null,
                        null,
                        false,
                        0,
                    ],
                    [
                        "48af09",
                        "LOT672  ",
                        "Poland",
                        1772630870,
                        1772630870,
                        20.9974,
                        52.1311,
                        198.12,
                        false,
                        69.3,
                        331.15,
                        -3.9,
                        null,
                        320.04,
                        "6563",
                        false,
                        0,
                    ],
                ],
            },
            statusCode: 200,
            statusText: "",
        },
    },
};

export const INITIAL_FIELDS: Array<Omit<FieldDef, "id">> = [
    { name: "icao24", type: "String", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "callsign", type: "String", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "origin_country", type: "String", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "time_position", type: "Integer", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "last_contact", type: "Integer", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "longitude", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "latitude", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "baro_altitude", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "on_ground", type: "Boolean", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "velocity", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "true_track", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "vertical_rate", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "geo_altitude", type: "Float", expression: "", mapEntries: [], useMapBuilder: false },
    { name: "squawk", type: "String", expression: "", mapEntries: [], useMapBuilder: false },
];

export const KAFKA_TOPIC_PROBE_NODE = {
    type: "Sink",
    id: "_spel-mapper-probe",
    ref: { typ: "kafka", parameters: [{ name: "Topic", expression: { language: "spel", expression: "''" } }] },
    additionalFields: { layoutData: { x: 0, y: 0 }, description: "" },
    endResult: null,
    isDisabled: null,
    branchParametersTemplate: [],
} as const;

/** Generate a SpEL record expression `{\n  key: expr,\n  ...\n}` from fields. */
export function genSpelFromFields(fields: FieldDef[]): string {
    const lines = fields.map((f) => {
        if (f.useMapBuilder && f.mapEntries.length > 0) {
            const entries = f.mapEntries.filter((e) => e.key).map((e) => `    ${e.key}: ${e.expression || "null"}`);
            return `  ${f.name}: {\n${entries.join(",\n")}\n  }`;
        }
        return `  ${f.name}: ${f.expression || "null"}`;
    });
    return `{\n${lines.join(",\n")}\n}`;
}
