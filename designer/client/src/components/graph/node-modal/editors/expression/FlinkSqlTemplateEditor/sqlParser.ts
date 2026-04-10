import type {
    CepState,
    Condition,
    DedupState,
    InputField,
    MatchOptions,
    Measure,
    PatternVariable,
    VisualEditorState,
    WindowDedupState,
    WindowTopNState,
} from "./types";
import { defaultCepState, defaultDedupState, defaultWindowDedupState, defaultWindowTopNState } from "./types";

export type ParsedResult = {
    state: VisualEditorState;
    warning?: string;
};

function parseSelectedFields(sql: string, availableFields: InputField[]): InputField[] {
    // Find innermost SELECT (not SELECT *) ... FROM record
    // NOTE: the generator always produces a newline after SELECT and always uses FROM record.
    // The regex relies on this format — if the generator changes, parsing may silently return [].
    const match = sql.match(/SELECT\s*\n([\s\S]*?)\s+FROM\s+(\w+)\b/i);
    if (!match) return availableFields;

    const selectedSources = new Set<string>();
    for (const line of match[1].split(",")) {
        const asMatch = line.trim().match(/^(\w+\.\w+)\s+AS\s+\w+/i);
        if (asMatch) selectedSources.add(asMatch[1]);
    }

    // Detect variable from field prefixes (e.g. "input.sensors AS sensors" -> variable "input").
    // This is reliable because the generator always uses varName.field AS alias in SELECT.
    // The fallback to match[2] (FROM table name = "record") will never match availableFields
    // and returns [] — which is the correct "no variable selected" state.
    const detectedVar = selectedSources.size > 0 ? [...selectedSources][0].split(".")[0] : match[2];

    const varFields = availableFields.filter((f) => f.source.startsWith(`${detectedVar}.`));
    if (varFields.length === 0) return [];

    return varFields.map((f) => ({ ...f, selected: selectedSources.has(f.source) }));
}

function extractMatchRecognizeAlias(sql: string, blockEndPos: number): string {
    const after = sql.substring(blockEndPos + 1).trimStart();
    const m = after.match(/^AS\s+(\w+)/i);
    return m ? m[1] : "match_result";
}

function extractMatchRecognizeContent(sql: string): { content: string; endPos: number } | null {
    const mrIndex = sql.search(/MATCH_RECOGNIZE\s*\(/i);
    if (mrIndex === -1) return null;
    const afterMr = sql.substring(mrIndex);
    const parenOffset = afterMr.indexOf("(");
    if (parenOffset === -1) return null;
    const startPos = mrIndex + parenOffset + 1;
    let depth = 1;
    for (let i = startPos; i < sql.length; i++) {
        if (sql[i] === "(") depth++;
        else if (sql[i] === ")") {
            depth--;
            if (depth === 0) return { content: sql.substring(startPos, i), endPos: i };
        }
    }
    return null;
}

function parseCondition(cp: string): Condition {
    cp = cp.trim();
    const isNull = cp.match(/^\w+\.(\w+)\s+(IS\s+NOT\s+NULL|IS\s+NULL)\s*$/i);
    if (isNull) return { mode: "simple", field: isNull[1], operator: isNull[2].replace(/\s+/, " "), value: "" };
    if (!cp.startsWith("(")) {
        const op = cp.match(/^\w+\.(\w+)\s*(!=|<=|>=|=|<|>)\s*(.+)/);
        if (op) return { mode: "simple", field: op[1], operator: op[2], value: op[3].trim() };
    }
    return { mode: "expr", expression: cp };
}

function parseMatchOptions(mr: string): MatchOptions {
    const options: MatchOptions = {
        rowsPerMatch: "ONE ROW PER MATCH",
        afterMatch: { type: "SKIP PAST LAST ROW" },
    };

    if (/ALL ROWS PER MATCH/i.test(mr)) {
        options.rowsPerMatch = "ALL ROWS PER MATCH";
    }

    const afterMatchSkipToFirst = mr.match(/AFTER MATCH SKIP TO FIRST\s+(\w+)/i);
    if (afterMatchSkipToFirst) {
        options.afterMatch = { type: "SKIP TO FIRST", variable: afterMatchSkipToFirst[1] };
        return options;
    }

    const afterMatchSkipToLast = mr.match(/AFTER MATCH SKIP TO LAST\s+(\w+)/i);
    if (afterMatchSkipToLast) {
        options.afterMatch = { type: "SKIP TO LAST", variable: afterMatchSkipToLast[1] };
        return options;
    }

    if (/AFTER MATCH SKIP TO NEXT ROW/i.test(mr)) {
        options.afterMatch = { type: "SKIP TO NEXT ROW" };
        return options;
    }

    // Default: SKIP PAST LAST ROW (already set)
    return options;
}

function parseCep(sql: string): { config: CepState; alias: string; warning?: string } {
    const state = defaultCepState();

    const extracted = extractMatchRecognizeContent(sql);
    if (!extracted) {
        return { config: state, alias: "match_result", warning: "Could not find MATCH_RECOGNIZE block" };
    }
    const { content: mr, endPos } = extracted;
    const alias = extractMatchRecognizeAlias(sql, endPos);

    const partitionMatch = mr.match(/PARTITION BY\s+(\S+)/i);
    if (partitionMatch) state.partitionBy = partitionMatch[1];

    const orderMatch = mr.match(/ORDER BY\s+(\S+)/i);
    if (orderMatch) state.orderBy = orderMatch[1];

    // Match options
    state.matchOptions = parseMatchOptions(mr);

    // Output alias
    state.outputAlias = alias;

    // PATTERN
    const patternMatch = mr.match(/PATTERN\s*\(([^)]+)\)/i);
    const patternVariables: PatternVariable[] = [];
    if (patternMatch) {
        for (const part of patternMatch[1].trim().split(/\s+/)) {
            const m = part.match(/^([A-Z])([+*?{}\d,]*)$/);
            if (m) {
                patternVariables.push({
                    name: m[1],
                    quantifier: m[2] || "",
                    conditions: [{ mode: "simple", field: "", operator: "=", value: "" }],
                });
            }
        }
    }

    if (patternVariables.length === 0) {
        return { config: state, alias, warning: "Could not parse PATTERN clause — please check the SQL manually" };
    }

    // DEFINE block
    const defineMatch = mr.match(/DEFINE\s*([\s\S]+)$/i);
    if (defineMatch) {
        const entries = defineMatch[1].split(/,(?=\s*\w+\s+AS\s)/);
        for (const entry of entries) {
            const varMatch = entry.match(/^\s*(\w+)\s+AS\s+([\s\S]+?)$/i);
            if (!varMatch) continue;
            const pv = patternVariables.find((p) => p.name === varMatch[1]);
            if (!pv) continue;
            const condStr = varMatch[2].trim();
            if (condStr === "TRUE") {
                pv.conditions = [{ mode: "simple", field: "", operator: "=", value: "" }];
                continue;
            }
            pv.conditions = condStr.split(/\s+AND\s+/i).map((cp) => parseCondition(cp));
        }
    }

    // MEASURES block — use flexible regex that handles both ONE ROW and ALL ROWS
    const measuresMatch = mr.match(/MEASURES\s*([\s\S]*?)\s*(?:ONE ROW PER MATCH|ALL ROWS PER MATCH)/i);
    if (measuresMatch) {
        const measures: Measure[] = [];
        for (const line of measuresMatch[1].split(",")) {
            const l = line.trim();
            if (!l || l.startsWith("--")) continue;
            const withFunc = l.match(/^(\w+)\(([^)]+)\)\s+AS\s+(\w+)$/i);
            if (withFunc) {
                const expr = withFunc[2];
                const variable = /^([A-Z])\./.exec(expr)?.[1] ?? "";
                measures.push({ variable, func: withFunc[1], expression: expr, alias: withFunc[3] });
                continue;
            }
            const plain = l.match(/^(.+?)\s+AS\s+(\w+)$/i);
            if (plain) {
                const expr = plain[1].trim();
                const variable = /^([A-Z])\./.exec(expr)?.[1] ?? "";
                measures.push({ variable, func: "", expression: expr, alias: plain[2] });
            }
        }
        state.measures = measures;
    }

    // WITHIN clause
    const withinMatch = mr.match(/PATTERN\s*\([^)]+\)\s*WITHIN\s+INTERVAL\s+'(\d+)'\s+(\w+)/i);
    if (withinMatch) state.within = `${withinMatch[1]} ${withinMatch[2].toUpperCase()}`;

    state.pattern = patternVariables;
    return { config: state, alias };
}

function parseDedup(sql: string): DedupState {
    const state = defaultDedupState();
    const overMatch = sql.match(/PARTITION BY\s+(.*?)\s+ORDER BY/is);
    if (overMatch) {
        state.partitionBy = overMatch[1]
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean);
    }
    return state;
}

function parseWindowInterval(sql: string): string {
    const m = sql.match(/INTERVAL\s+'(\d+)'\s+(\w+)/i);
    return m ? `${m[1]} ${m[2].toUpperCase()}` : "1 HOUR";
}

function parseWindowDedup(sql: string): WindowDedupState {
    const state = defaultWindowDedupState();
    state.windowSize = parseWindowInterval(sql);
    const overMatch = sql.match(/PARTITION BY\s+(.*?)\s+ORDER BY/is);
    if (overMatch) {
        // strip window_start, window_end from partitionBy
        state.partitionBy = overMatch[1]
            .split(",")
            .map((s) => s.trim())
            .filter((s) => s && s !== "window_start" && s !== "window_end");
    }
    return state;
}

function parseWindowTopN(sql: string): WindowTopNState {
    const state = defaultWindowTopNState();
    state.windowSize = parseWindowInterval(sql);
    const nMatch = sql.match(/WHERE rn <=\s*(\d+)/i);
    if (nMatch) state.n = parseInt(nMatch[1], 10);
    const overMatch = sql.match(/PARTITION BY\s+(.*?)\s+ORDER BY\s+(.*?)\s*\)\s+AS rn/is);
    if (overMatch) {
        state.partitionBy = overMatch[1]
            .split(",")
            .map((s) => s.trim())
            .filter((s) => s && s !== "window_start" && s !== "window_end");
        const orderParts = overMatch[2].trim().split(/\s+/);
        state.orderDir = orderParts.at(-1)?.toUpperCase() === "ASC" ? "ASC" : "DESC";
        state.orderBy = orderParts.slice(0, -1).join(" ");
    }
    return state;
}

// Marker prefix written by index.tsx into every stored visual-template expression.
// parseSql requires this marker — without it the SQL is treated as generic and null is returned.
// This prevents heuristic mis-detection of hand-written SQL (e.g. a custom dedup written in generic mode).
export const TEMPLATE_MARKER_PREFIX = "--@nu:";

export function parseSql(sql: string, availableFields: InputField[]): ParsedResult | null {
    const trimmed = sql.trim();
    if (!trimmed) return null;

    // Marker present → detect visual template type
    const markerMatch = trimmed.match(/^--@nu:(\w+)\n([\s\S]*)$/);
    if (markerMatch) {
        const templateType = markerMatch[1];
        const actualSql = markerMatch[2].trim();
        if (!actualSql) return null;

        // Placeholder SQL (e.g. "-- Select input fields first") — return default state, no warning
        if (actualSql.startsWith("--")) {
            switch (templateType) {
                case "cep":
                    return { state: { inputFields: [], template: { type: "cep", config: defaultCepState() } } };
                case "dedup":
                    return { state: { inputFields: [], template: { type: "dedup", config: defaultDedupState() } } };
                case "windowDedup":
                    return { state: { inputFields: [], template: { type: "windowDedup", config: defaultWindowDedupState() } } };
                case "windowTopN":
                    return { state: { inputFields: [], template: { type: "windowTopN", config: defaultWindowTopNState() } } };
                default:
                    return null;
            }
        }

        const inputFields = parseSelectedFields(actualSql, availableFields);

        switch (templateType) {
            case "cep": {
                const { config, alias, warning } = parseCep(actualSql);
                return { state: { inputFields, template: { type: "cep", config }, matchAlias: alias }, warning };
            }
            case "dedup":
                return { state: { inputFields, template: { type: "dedup", config: parseDedup(actualSql) } } };
            case "windowDedup":
                return { state: { inputFields, template: { type: "windowDedup", config: parseWindowDedup(actualSql) } } };
            case "windowTopN":
                return { state: { inputFields, template: { type: "windowTopN", config: parseWindowTopN(actualSql) } } };
            default:
                return null;
        }
    }

    // No marker — MATCH_RECOGNIZE is unambiguous: auto-detect as CEP (migration path for pre-marker SQL).
    // Dedup/windowDedup/windowTopN require a marker to avoid false positives on hand-written SQL.
    if (!trimmed.startsWith("--") && /MATCH_RECOGNIZE\s*\(/i.test(trimmed)) {
        const inputFields = parseSelectedFields(trimmed, availableFields);
        const { config, alias, warning } = parseCep(trimmed);
        return { state: { inputFields, template: { type: "cep", config }, matchAlias: alias }, warning };
    }

    // No marker — never auto-switch to a visual template.
    // Still try to restore the selected input variable from the SELECT clause.
    if (trimmed.startsWith("--")) return null;
    const inputFields = parseSelectedFields(trimmed, availableFields);
    if (inputFields.some((f) => f.selected)) {
        return { state: { inputFields, template: { type: "generic" } } };
    }

    return null;
}
