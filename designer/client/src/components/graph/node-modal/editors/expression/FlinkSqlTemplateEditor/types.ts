import type { VariableTypes } from "../../../../../../types/validation";

export interface InputField {
    name: string;
    source: string;
    alias: string;
    type: string;
    selected: boolean;
}

export const EXCLUDED_INPUT_VARIABLES = new Set(["inputMeta"]);

export function buildInputFieldsForVariable(varName: string, variableTypes: VariableTypes): InputField[] {
    const typingResult = variableTypes[varName];
    if (!typingResult || !("fields" in typingResult) || !typingResult.fields) return [];
    return Object.entries(typingResult.fields as Record<string, object>)
        .filter(([fieldName]) => fieldName !== "record_time")
        .map(([fieldName, fieldType]) => ({
            name: `${varName}.${fieldName}`,
            source: `${varName}.${fieldName}`,
            alias: fieldName,
            type:
                ("refClazzName" in fieldType && typeof fieldType.refClazzName === "string" ? fieldType.refClazzName : "")
                    ?.split(".")
                    ?.pop() ?? "Any",
            selected: true,
        }));
}

export type Condition = { mode: "simple"; field: string; operator: string; value: string } | { mode: "expr"; expression: string };

export interface PatternVariable {
    name: string;
    description?: string;
    quantifier?: string;
    conditions: Condition[];
}

export interface Measure {
    variable: string;
    func: string;
    expression: string;
    alias: string;
}

export type AfterMatchStrategy =
    | { type: "SKIP PAST LAST ROW" }
    | { type: "SKIP TO NEXT ROW" }
    | { type: "SKIP TO FIRST"; variable: string }
    | { type: "SKIP TO LAST"; variable: string };

export interface MatchOptions {
    rowsPerMatch: "ONE ROW PER MATCH" | "ALL ROWS PER MATCH";
    afterMatch: AfterMatchStrategy;
}

export interface CepState {
    partitionBy: string;
    orderBy: string;
    pattern: PatternVariable[];
    measures: Measure[];
    matchOptions: MatchOptions;
    outputAlias: string;
    within?: string; // e.g. "60 MINUTE", undefined = no constraint
}

export interface DedupState {
    partitionBy: string[];
}

export interface WindowDedupState {
    partitionBy: string[];
    windowSize: string;
}

export interface WindowTopNState {
    partitionBy: string[];
    orderBy: string;
    orderDir: "ASC" | "DESC";
    n: number;
    windowSize: string;
}

export type TemplateType = "generic" | "cep" | "dedup" | "windowDedup" | "windowTopN";

export type TemplateState =
    | { type: "generic" }
    | { type: "cep"; config: CepState }
    | { type: "dedup"; config: DedupState }
    | { type: "windowDedup"; config: WindowDedupState }
    | { type: "windowTopN"; config: WindowTopNState };

export interface VisualEditorState {
    inputFields: InputField[];
    template: TemplateState;
    matchAlias?: string;
}

export const PATTERN_VARIABLE_COLORS: Record<string, string> = {
    A: "#6ea8c8",
    B: "#d4c46a",
    C: "#7ec87e",
    D: "#c87eaa",
    E: "#c8956e",
    F: "#8e7ec8",
};

export function getPatternVariableColor(name: string): string {
    return PATTERN_VARIABLE_COLORS[name] ?? "#888";
}

export const CONDITION_OPERATORS = ["=", "!=", "<", "<=", ">", ">=", "IS NULL", "IS NOT NULL"];

export const defaultCepState = (): CepState => ({
    partitionBy: "",
    orderBy: "record_time",
    pattern: [
        {
            name: "A",
            description: "",
            quantifier: "",
            conditions: [{ mode: "simple", field: "", operator: "=", value: "" }],
        },
    ],
    measures: [],
    matchOptions: {
        rowsPerMatch: "ONE ROW PER MATCH",
        afterMatch: { type: "SKIP PAST LAST ROW" },
    },
    outputAlias: "match_result",
});

export const defaultDedupState = (): DedupState => ({
    partitionBy: [],
});

export const defaultWindowDedupState = (): WindowDedupState => ({
    partitionBy: [],
    windowSize: "1 HOUR",
});

export const defaultWindowTopNState = (): WindowTopNState => ({
    partitionBy: [],
    orderBy: "",
    orderDir: "DESC",
    n: 3,
    windowSize: "1 HOUR",
});
