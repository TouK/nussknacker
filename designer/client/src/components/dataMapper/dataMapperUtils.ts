import type { TypingResult } from "../../types/definition";

// ─── Types ────────────────────────────────────────────────────────────────────

export type NuType =
    | "String"
    | "Integer"
    | "Long"
    | "Float"
    | "Double"
    | "Boolean"
    | "BigDecimal"
    | "List"
    | "Map"
    | "ZonedDateTime"
    | "LocalDate"
    | "LocalDateTime"
    | "LocalTime"
    | "Instant"
    | "Duration"
    | "Period"
    | "OffsetDateTime"
    | "Any";

const NU_TYPE_CLASS_MAP: Partial<Record<NuType, string[]>> = {
    String: ["java.lang.String"],
    Integer: ["java.lang.Integer"],
    Long: ["java.lang.Long"],
    Float: ["java.lang.Float"],
    Double: ["java.lang.Double"],
    Boolean: ["java.lang.Boolean"],
    BigDecimal: ["java.math.BigDecimal"],
    List: ["java.util.List"],
    Map: ["java.util.Map"],
    ZonedDateTime: ["java.time.ZonedDateTime"],
    LocalDate: ["java.time.LocalDate"],
    LocalDateTime: ["java.time.LocalDateTime"],
    LocalTime: ["java.time.LocalTime"],
    Instant: ["java.time.Instant"],
    Duration: ["java.time.Duration"],
    Period: ["java.time.Period"],
    OffsetDateTime: ["java.time.OffsetDateTime"],
};

/** Maps a Java refClazzName to the closest NuType. */
export function refClazzToNuType(refClazzName: string | undefined): NuType {
    if (!refClazzName) return "Any";
    for (const [nuType, classes] of Object.entries(NU_TYPE_CLASS_MAP) as [NuType, string[]][]) {
        if (classes.some((c) => refClazzName === c || refClazzName.startsWith(c))) return nuType;
    }
    return "Any";
}

/** Returns a type-mismatch error message if the backend-inferred type doesn't match the declared NuType. */
export function checkTypeCompatibility(expressionType: TypingResult | undefined, nuType: NuType): string | null {
    if (nuType === "Any" || !expressionType) return null;
    const expectedClasses = NU_TYPE_CLASS_MAP[nuType];
    if (!expectedClasses) return null;
    const actualClass = expressionType.refClazzName;
    if (!actualClass) return null;
    if (expectedClasses.some((c) => actualClass === c || actualClass.startsWith(c))) return null;
    const actual = expressionType.display ?? actualClass.split(".").pop() ?? actualClass;
    return `Type mismatch: expected ${nuType} but expression returns ${actual}`;
}

export interface FieldDef {
    id: number;
    name: string;
    type: NuType;
    expression: string;
    children: FieldDef[]; // non-empty or isRecord=true → nested record mode
    isRecord: boolean;
}

export type ContextData = Record<string, unknown>;

export interface TopicEntry {
    topic: string;
    schema: Record<string, unknown>;
    /** Display name of the Kafka connector this topic belongs to. */
    sourceLabel?: string;
}

// ─── Constants ────────────────────────────────────────────────────────────────

export const NU_TYPES: NuType[] = [
    "String",
    "Integer",
    "Long",
    "Float",
    "Double",
    "Boolean",
    "BigDecimal",
    "List",
    "Map",
    "ZonedDateTime",
    "LocalDate",
    "LocalDateTime",
    "LocalTime",
    "Instant",
    "Duration",
    "Period",
    "OffsetDateTime",
    "Any",
];

// ─── Type conversions ─────────────────────────────────────────────────────────

export const TYPE_CONVERSIONS: Partial<Record<NuType, Partial<Record<NuType, (expr: string) => string>>>> = {
    ZonedDateTime: {
        Long: (e) => `#DATE.toEpochMilli(${e})`,
        Instant: (e) => `${e}.toInstant()`,
        LocalDate: (e) => `${e}.toLocalDate()`,
        LocalDateTime: (e) => `${e}.toLocalDateTime()`,
        LocalTime: (e) => `${e}.toLocalTime()`,
        OffsetDateTime: (e) => `${e}.toOffsetDateTime()`,
        String: (e) => `${e}.toString()`,
    },
    Instant: {
        Long: (e) => `${e}.toEpochMilli()`,
        ZonedDateTime: (e) => `${e}.atZone(#DATE.defaultTimeZone)`,
        LocalDate: (e) => `${e}.atZone(#DATE.defaultTimeZone).toLocalDate()`,
        LocalDateTime: (e) => `${e}.atZone(#DATE.defaultTimeZone).toLocalDateTime()`,
        LocalTime: (e) => `${e}.atZone(#DATE.defaultTimeZone).toLocalTime()`,
        OffsetDateTime: (e) => `${e}.atOffset(#DATE.UTCOffset)`,
        String: (e) => `${e}.toString()`,
    },
    Long: {
        Instant: (e) => `#DATE.toInstant(${e})`,
        ZonedDateTime: (e) => `#DATE.toInstant(${e}).atZone(#DATE.defaultTimeZone)`,
        LocalDate: (e) => `#DATE.toInstant(${e}).atZone(#DATE.defaultTimeZone).toLocalDate()`,
        LocalDateTime: (e) => `#DATE.toInstant(${e}).atZone(#DATE.defaultTimeZone).toLocalDateTime()`,
        LocalTime: (e) => `#DATE.toInstant(${e}).atZone(#DATE.defaultTimeZone).toLocalTime()`,
    },
    LocalDate: {
        ZonedDateTime: (e) => `${e}.atStartOfDay(#DATE.defaultTimeZone)`,
        LocalDateTime: (e) => `${e}.atStartOfDay()`,
        Instant: (e) => `${e}.atStartOfDay(#DATE.defaultTimeZone).toInstant()`,
        Long: (e) => `#DATE.toEpochMilli(${e}.atStartOfDay(#DATE.defaultTimeZone))`,
        String: (e) => `${e}.toString()`,
    },
    LocalDateTime: {
        ZonedDateTime: (e) => `${e}.atZone(#DATE.defaultTimeZone)`,
        LocalDate: (e) => `${e}.toLocalDate()`,
        LocalTime: (e) => `${e}.toLocalTime()`,
        Instant: (e) => `${e}.atZone(#DATE.defaultTimeZone).toInstant()`,
        Long: (e) => `#DATE.toEpochMilli(${e}, #DATE.defaultTimeZone)`,
        String: (e) => `${e}.toString()`,
    },
    LocalTime: {
        String: (e) => `${e}.toString()`,
        LocalDateTime: (e) => `${e}.atDate(#DATE.now().toLocalDate())`,
        ZonedDateTime: (e) => `${e}.atDate(#DATE.now().toLocalDate()).atZone(#DATE.defaultTimeZone)`,
    },
    OffsetDateTime: {
        ZonedDateTime: (e) => `${e}.toZonedDateTime()`,
        Instant: (e) => `${e}.toInstant()`,
        Long: (e) => `#DATE.toEpochMilli(${e})`,
        LocalDate: (e) => `${e}.toLocalDate()`,
        LocalDateTime: (e) => `${e}.toLocalDateTime()`,
        LocalTime: (e) => `${e}.toLocalTime()`,
        String: (e) => `${e}.toString()`,
    },
    String: {
        ZonedDateTime: (e) => `#DATE_FORMAT.parseZonedDateTime(${e}, "yyyyMMdd-HH:mm:ss.SSS")`,
        LocalDateTime: (e) => `#DATE_FORMAT.parseLocalDateTime(${e}, "yyyyMMdd-HH:mm:ss.SSS")`,
        LocalDate: (e) => `#DATE_FORMAT.parseLocalDate(${e}, "yyyyMMdd-HH:mm:ss.SSS")`,
    },
    Duration: {
        Long: (e) => `${e}.toMillis()`,
        String: (e) => `${e}.toString()`,
    },
    Period: {
        String: (e) => `${e}.toString()`,
    },
};

/** Apply a type conversion from sourceType to targetType, or return expr unchanged if no conversion available. */
export function applyTypeConversion(expr: string, sourceType: NuType | undefined, targetType: NuType): string {
    if (!sourceType || sourceType === targetType || targetType === "Any" || sourceType === "Any") return expr;
    const conversion = TYPE_CONVERSIONS[sourceType]?.[targetType];
    return conversion ? conversion(expr) : expr;
}

/** Get the NuType of a value at a dotted path within variableTypes (strips leading # and ? from path). */
export function getNuTypeAtPath(variableTypes: Record<string, TypingResult>, path: string): NuType | undefined {
    const parts = path.replace(/^#/, "").replace(/\?/g, "").split(".").filter(Boolean);
    if (parts.length === 0) return undefined;
    let current: TypingResult | undefined = variableTypes[parts[0]];
    if (!current) return undefined;
    for (let i = 1; i < parts.length; i++) {
        const fields = (current as { fields?: Record<string, TypingResult> }).fields;
        if (!fields) return undefined;
        current = fields[parts[i]];
        if (!current) return undefined;
    }
    return refClazzToNuType(current.refClazzName);
}

// ─── ID counter ───────────────────────────────────────────────────────────────

export let _nextId = 1;

/** Consume and return the next unique id. */
export function nextId(): number {
    return _nextId++;
}

// ─── Utilities ────────────────────────────────────────────────────────────────

export function makeField(name = "", type: NuType = "String"): FieldDef {
    return { id: _nextId++, name, type, expression: "", children: [], isRecord: false };
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

export function fieldsFromSample(obj: unknown): FieldDef[] {
    if (typeof obj !== "object" || obj === null || Array.isArray(obj)) return [];
    return Object.entries(obj as Record<string, unknown>).map(([k, v]) => {
        if (typeof v === "object" && v !== null && !Array.isArray(v)) {
            const children = fieldsFromSample(v);
            return { ...makeField(k, "Map"), isRecord: true, children };
        }
        return makeField(k, inferNuType(v));
    });
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

/** Parse a SpEL record expression `{ key: expr, ... }` back into FieldDef[] (recursive). */
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

        // Nested record → recurse
        if (val.startsWith("{") && val.endsWith("}")) {
            const children = parseSpelToFields(val) ?? [];
            const f = makeField(name, "Map");
            f.isRecord = true;
            f.children = children;
            fields.push(f);
            continue;
        }

        // null → empty field
        if (val === "null") {
            fields.push(makeField(name, "Any"));
            continue;
        }

        // Any SpEL expression
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
    { name: "icao24", type: "String", expression: "", children: [], isRecord: false },
    { name: "callsign", type: "String", expression: "", children: [], isRecord: false },
    { name: "origin_country", type: "String", expression: "", children: [], isRecord: false },
    { name: "time_position", type: "Integer", expression: "", children: [], isRecord: false },
    { name: "last_contact", type: "Integer", expression: "", children: [], isRecord: false },
    { name: "longitude", type: "Float", expression: "", children: [], isRecord: false },
    { name: "latitude", type: "Float", expression: "", children: [], isRecord: false },
    { name: "baro_altitude", type: "Float", expression: "", children: [], isRecord: false },
    { name: "on_ground", type: "Boolean", expression: "", children: [], isRecord: false },
    { name: "velocity", type: "Float", expression: "", children: [], isRecord: false },
    { name: "true_track", type: "Float", expression: "", children: [], isRecord: false },
    { name: "vertical_rate", type: "Float", expression: "", children: [], isRecord: false },
    { name: "geo_altitude", type: "Float", expression: "", children: [], isRecord: false },
    { name: "squawk", type: "String", expression: "", children: [], isRecord: false },
];

export const EXPR_PROBE_NODE_BASE = {
    type: "Variable",
    id: "_dm-expr-probe",
    varName: "_probe",
    additionalFields: { layoutData: { x: 0, y: 0 }, description: "" },
} as const;

export const KAFKA_TOPIC_PROBE_NODE = {
    type: "Sink",
    id: "_spel-mapper-probe",
    ref: { typ: "kafka", parameters: [{ name: "Topic", expression: { language: "spel", expression: "''" } }] },
    additionalFields: { layoutData: { x: 0, y: 0 }, description: "" },
    endResult: null,
    isDisabled: null,
    branchParametersTemplate: [],
} as const;

/** Generate a SpEL record expression `{\n  key: expr,\n  ...\n}` from fields (recursive). */
export function genSpelFromFields(fields: FieldDef[], depth = 0): string {
    const outerIndent = "  ".repeat(depth);
    const innerIndent = "  ".repeat(depth + 1);
    const lines = fields.map((f) => {
        if (f.isRecord) {
            if (f.children.length === 0) return `${innerIndent}${f.name}: {}`;
            return `${innerIndent}${f.name}: ${genSpelFromFields(f.children, depth + 1)}`;
        }
        return `${innerIndent}${f.name}: ${f.expression || "null"}`;
    });
    return `{\n${lines.join(",\n")}\n${outerIndent}}`;
}
