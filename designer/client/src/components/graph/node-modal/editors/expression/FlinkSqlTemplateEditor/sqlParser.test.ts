import type { VariableTypes } from "../../../../../../types/validation";
import { generateCepSql, generateDedupSql, generateWindowDedupSql, generateWindowTopNSql } from "./sqlGenerator";
import { parseSql, TEMPLATE_MARKER_PREFIX } from "./sqlParser";
import type { CepState, DedupState, InputField, WindowDedupState, WindowTopNState } from "./types";
import { buildInputFieldsForVariable, defaultCepState, defaultDedupState, defaultWindowDedupState, defaultWindowTopNState } from "./types";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function fields(...names: string[]): InputField[] {
    return names.map((name) => ({
        name: `input.${name}`,
        source: `input.${name}`,
        alias: name,
        type: "String",
        selected: true,
    }));
}

const availableFields = fields("icao24", "callsign", "on_ground", "velocity");

function withMarker(type: string, sql: string): string {
    return `${TEMPLATE_MARKER_PREFIX}${type}\n${sql}`;
}

function dedupConfig(result: ReturnType<typeof parseSql>): DedupState {
    expect(result?.state.template.type).toBe("dedup");
    return (result!.state.template as Extract<NonNullable<typeof result>["state"]["template"], { type: "dedup" }>).config;
}

function windowDedupConfig(result: ReturnType<typeof parseSql>): WindowDedupState {
    expect(result?.state.template.type).toBe("windowDedup");
    return (result!.state.template as Extract<NonNullable<typeof result>["state"]["template"], { type: "windowDedup" }>).config;
}

function windowTopNConfig(result: ReturnType<typeof parseSql>): WindowTopNState {
    expect(result?.state.template.type).toBe("windowTopN");
    return (result!.state.template as Extract<NonNullable<typeof result>["state"]["template"], { type: "windowTopN" }>).config;
}

function cepConfig(result: ReturnType<typeof parseSql>): CepState {
    expect(result?.state.template.type).toBe("cep");
    return (result!.state.template as Extract<NonNullable<typeof result>["state"]["template"], { type: "cep" }>).config;
}

// ---------------------------------------------------------------------------
// buildInputFieldsForVariable
// ---------------------------------------------------------------------------

describe("buildInputFieldsForVariable", () => {
    const variableTypes = {
        input: {
            fields: {
                icao24: { refClazzName: "java.lang.String" },
                velocity: { refClazzName: "java.lang.Double" },
                record_time: { refClazzName: "java.time.Instant" },
            },
        },
        inputMeta: { fields: { id: { refClazzName: "java.lang.String" } } },
    } as unknown as VariableTypes;

    it("returns fields for known variable", () => {
        const result = buildInputFieldsForVariable("input", variableTypes);
        expect(result.map((f) => f.alias)).toEqual(["icao24", "velocity"]);
    });

    it("excludes record_time", () => {
        const result = buildInputFieldsForVariable("input", variableTypes);
        expect(result.find((f) => f.alias === "record_time")).toBeUndefined();
    });

    it("returns [] for variable without fields property", () => {
        const noFields = { input: { refClazzName: "java.lang.String" } } as unknown as VariableTypes;
        expect(buildInputFieldsForVariable("input", noFields)).toEqual([]);
    });

    it("returns [] for unknown variable", () => {
        expect(buildInputFieldsForVariable("unknown", variableTypes)).toEqual([]);
    });

    it("sets source as varName.fieldName", () => {
        const result = buildInputFieldsForVariable("input", variableTypes);
        expect(result[0].source).toBe("input.icao24");
    });

    it("extracts short type name from refClazzName", () => {
        const result = buildInputFieldsForVariable("input", variableTypes);
        expect(result.find((f) => f.alias === "icao24")?.type).toBe("String");
        expect(result.find((f) => f.alias === "velocity")?.type).toBe("Double");
    });
});

// ---------------------------------------------------------------------------
// parseSql — marker detection
// ---------------------------------------------------------------------------

describe("parseSql — marker detection", () => {
    it("returns null for empty string", () => {
        expect(parseSql("", availableFields)).toBeNull();
    });

    it("returns null for comment-only SQL (no marker)", () => {
        expect(parseSql("-- Select input fields first", availableFields)).toBeNull();
    });

    it("returns null for unknown marker type", () => {
        expect(parseSql(withMarker("unknown", "SELECT 1"), availableFields)).toBeNull();
    });

    it("returns null when SQL after marker is empty", () => {
        expect(parseSql(`${TEMPLATE_MARKER_PREFIX}dedup\n`, availableFields)).toBeNull();
    });

    it("auto-detects CEP from MATCH_RECOGNIZE without marker", () => {
        const sql = generateCepSql(availableFields, defaultCepState());
        const result = parseSql(sql, availableFields);
        expect(result?.state.template.type).toBe("cep");
    });

    it("does NOT auto-detect dedup without marker", () => {
        const sql = generateDedupSql(availableFields, defaultDedupState());
        const result = parseSql(sql, availableFields);
        expect(result?.state.template.type === "dedup").toBe(false);
    });
});

// ---------------------------------------------------------------------------
// parseSql — dedup roundtrip
// ---------------------------------------------------------------------------

describe("parseSql — dedup roundtrip", () => {
    const config = { ...defaultDedupState(), partitionBy: ["icao24", "callsign"] };

    it("restores partitionBy", () => {
        const sql = withMarker("dedup", generateDedupSql(availableFields, config));
        const c = dedupConfig(parseSql(sql, availableFields));
        expect(c.partitionBy).toEqual(["icao24", "callsign"]);
    });

    it("restores inputFields variable", () => {
        const sql = withMarker("dedup", generateDedupSql(availableFields, config));
        const result = parseSql(sql, availableFields);
        expect(result?.state.inputFields.length).toBeGreaterThan(0);
        expect(result?.state.inputFields.every((f) => f.source.startsWith("input."))).toBe(true);
    });

    it("does not include record_time in inputFields", () => {
        const sql = withMarker("dedup", generateDedupSql(availableFields, config));
        const result = parseSql(sql, availableFields);
        expect(result?.state.inputFields.find((f) => f.alias === "record_time")).toBeUndefined();
    });

    it("returns [] inputFields when variable not in availableFields", () => {
        const otherFields = fields("sensor1", "sensor2").map((f) => ({
            ...f,
            source: `other.${f.alias}`,
            name: `other.${f.alias}`,
        }));
        const sql = withMarker("dedup", generateDedupSql(availableFields, config));
        const result = parseSql(sql, otherFields);
        expect(result?.state.inputFields).toEqual([]);
    });
});

// ---------------------------------------------------------------------------
// parseSql — windowDedup roundtrip
// ---------------------------------------------------------------------------

describe("parseSql — windowDedup roundtrip", () => {
    const config = { ...defaultWindowDedupState(), partitionBy: ["icao24"], windowSize: "5 MINUTE" };

    it("restores partitionBy and windowSize", () => {
        const sql = withMarker("windowDedup", generateWindowDedupSql(availableFields, config));
        const c = windowDedupConfig(parseSql(sql, availableFields));
        expect(c.partitionBy).toEqual(["icao24"]);
        expect(c.windowSize).toBe("5 MINUTE");
    });

    it("excludes window_start and window_end from partitionBy", () => {
        const sql = withMarker("windowDedup", generateWindowDedupSql(availableFields, config));
        const c = windowDedupConfig(parseSql(sql, availableFields));
        expect(c.partitionBy).not.toContain("window_start");
        expect(c.partitionBy).not.toContain("window_end");
    });
});

// ---------------------------------------------------------------------------
// parseSql — windowTopN roundtrip
// ---------------------------------------------------------------------------

describe("parseSql — windowTopN roundtrip", () => {
    const config: WindowTopNState = {
        ...defaultWindowTopNState(),
        partitionBy: ["icao24"],
        orderBy: "velocity",
        orderDir: "DESC",
        n: 5,
        windowSize: "1 HOUR",
    };

    it("restores n, orderBy, orderDir, windowSize, partitionBy", () => {
        const sql = withMarker("windowTopN", generateWindowTopNSql(availableFields, config));
        const c = windowTopNConfig(parseSql(sql, availableFields));
        expect(c.n).toBe(5);
        expect(c.orderBy).toBe("velocity");
        expect(c.orderDir).toBe("DESC");
        expect(c.windowSize).toBe("1 HOUR");
        expect(c.partitionBy).toEqual(["icao24"]);
    });
});

// ---------------------------------------------------------------------------
// parseSql — CEP roundtrip
// ---------------------------------------------------------------------------

describe("parseSql — CEP roundtrip", () => {
    const config: CepState = {
        ...defaultCepState(),
        partitionBy: "icao24",
        orderBy: "record_time",
        within: "1 MINUTE",
        pattern: [
            {
                name: "A",
                quantifier: "",
                conditions: [{ mode: "simple", field: "on_ground", operator: "=", value: "FALSE" }],
            },
            {
                name: "B",
                quantifier: "",
                conditions: [{ mode: "simple", field: "on_ground", operator: "=", value: "TRUE" }],
            },
        ],
        measures: [
            { variable: "A", func: "", expression: "A.icao24", alias: "aircraft" },
            { variable: "A", func: "LAST", expression: "A.velocity", alias: "last_velocity" },
        ],
    };

    it("restores partitionBy, orderBy, within", () => {
        const sql = withMarker("cep", generateCepSql(availableFields, config));
        const c = cepConfig(parseSql(sql, availableFields));
        expect(c.partitionBy).toBe("icao24");
        expect(c.orderBy).toBe("record_time");
        expect(c.within).toBe("1 MINUTE");
    });

    it("restores pattern variables and conditions", () => {
        const sql = withMarker("cep", generateCepSql(availableFields, config));
        const c = cepConfig(parseSql(sql, availableFields));
        expect(c.pattern.map((p) => p.name)).toEqual(["A", "B"]);
        expect(c.pattern[0].conditions[0]).toMatchObject({ mode: "simple", field: "on_ground", value: "FALSE" });
    });

    it("restores measures with func and plain expression", () => {
        const sql = withMarker("cep", generateCepSql(availableFields, config));
        const c = cepConfig(parseSql(sql, availableFields));
        expect(c.measures.find((m) => m.alias === "aircraft")).toMatchObject({ func: "", expression: "A.icao24" });
        expect(c.measures.find((m) => m.alias === "last_velocity")).toMatchObject({ func: "LAST", expression: "A.velocity" });
    });
});

// ---------------------------------------------------------------------------
// generateDedupSql — FROM record
// ---------------------------------------------------------------------------

describe("generateDedupSql", () => {
    it("always uses FROM record in inner SELECT", () => {
        const sql = generateDedupSql(availableFields, defaultDedupState());
        expect(sql).toContain("FROM record");
    });

    it("uses PROCTIME() for ORDER BY", () => {
        const sql = generateDedupSql(availableFields, defaultDedupState());
        expect(sql).toContain("ORDER BY PROCTIME() ASC");
    });

    it("returns placeholder when no fields selected", () => {
        const unselected = availableFields.map((f) => ({ ...f, selected: false }));
        expect(generateDedupSql(unselected, defaultDedupState())).toBe("-- Select input fields first");
    });

    it("includes partition key in SQL", () => {
        const config = { ...defaultDedupState(), partitionBy: ["icao24", "callsign"] };
        const sql = generateDedupSql(availableFields, config);
        expect(sql).toContain("PARTITION BY icao24, callsign");
    });
});
