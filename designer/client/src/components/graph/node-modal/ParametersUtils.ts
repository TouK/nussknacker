import { get } from "lodash";

import type { UIParameter } from "../../../types/definition";
import type { NodeType, Parameter } from "../../../types/node";
import type { ProcessAdditionalFields } from "../../../types/scenarioGraph";
import { isRequestSource, scenarioPropertiesToNodeProperties } from "./requestSourceAddons";
import { setImmutable } from "./setImmutable";

const parametersPath = (node) => {
    switch (node.type) {
        case "CustomNode":
        case "Join":
            return `parameters`;
        case "Source":
        case "Sink":
        case "FragmentInput":
            return `ref.parameters`;
        case "Enricher":
        case "Processor":
            return `service.parameters`;
        default:
            return null;
    }
};

// ─── Kafka raw editor ↔ dynamic mode migration helpers ───────────────────────

const KAFKA_SYSTEM_PARAMS = new Set(["Topic", "Schema version", "Key", "Raw editor", "Content type", "Value validation mode"]);

function splitTopLevelParts(inner: string): string[] {
    const parts: string[] = [];
    let braces = 0,
        parens = 0,
        brackets = 0,
        start = 0;
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

/**
 * Flatten a SpEL record into a name→expression map.
 * When `knownParamNames` is provided, recursion stops at keys matching known params,
 * allowing nested `{combinedState: {key: ..., tool_record: {...}}}` to be distributed
 * back to individual dotted params like `combinedState.key`, `combinedState.tool_record`.
 * Without `knownParamNames`, only the top level is parsed (legacy flat format).
 */
function flattenSpelRecord(expression: string, knownParamNames?: Set<string>, prefix = ""): Record<string, string> | null {
    const trimmed = expression.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null;
    const inner = trimmed.slice(1, -1).trim();
    const result: Record<string, string> = {};
    for (const part of splitTopLevelParts(inner)) {
        const colonIdx = part.indexOf(":");
        if (colonIdx === -1) continue;
        const rawName = part.slice(0, colonIdx).trim();
        const name = rawName.replace(/^["'](.*)["']$/, "$1");
        const val = part.slice(colonIdx + 1).trim();
        if (!name) continue;
        const fullKey = prefix ? `${prefix}.${name}` : name;
        if (!knownParamNames || knownParamNames.has(fullKey)) {
            result[fullKey] = reformatIfSpelRecord(val, 0);
        } else if (val.startsWith("{") && val.endsWith("}")) {
            const nested = flattenSpelRecord(val, knownParamNames, fullKey);
            if (nested) Object.assign(result, nested);
            else result[fullKey] = val;
        } else {
            result[fullKey] = val;
        }
    }
    return Object.keys(result).length > 0 ? result : null;
}

/** Re-indent a SpEL record expression string to match the given nesting depth. */
function reformatIfSpelRecord(expr: string, depth: number): string {
    const trimmed = expr.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return expr;
    const inner = trimmed.slice(1, -1).trim();
    if (!inner) return "{}";
    const parts = splitTopLevelParts(inner);
    if (parts.length === 0) return "{}";
    const indent = "  ".repeat(depth + 1);
    const closing = "  ".repeat(depth);
    const lines = parts.map((part) => {
        const colonIdx = part.indexOf(":");
        if (colonIdx === -1) return `${indent}${part.trim()}`;
        const key = part.slice(0, colonIdx).trim();
        const val = part.slice(colonIdx + 1).trim();
        return `${indent}${key}: ${reformatIfSpelRecord(val, depth + 1)}`;
    });
    return `{\n${lines.join(",\n")}\n${closing}}`;
}

/** Recursively build a nested SpEL record from a tree of `{key: string|nested}` objects. */
function serializeSpelRecord(obj: Record<string, unknown>, depth = 0): string {
    const indent = "  ".repeat(depth + 1);
    const closing = "  ".repeat(depth);
    const lines = Object.entries(obj).map(([k, v]) => {
        const value =
            typeof v === "object" && v !== null
                ? serializeSpelRecord(v as Record<string, unknown>, depth + 1)
                : reformatIfSpelRecord(v as string, depth + 1);
        return `${indent}${k}: ${value}`;
    });
    return `{\n${lines.join(",\n")}\n${closing}}`;
}

/** Group dotted param names (e.g. "combinedState.key") into a nested SpEL record expression. */
function buildNestedSpelRecord(params: Parameter[]): string {
    const root: Record<string, unknown> = {};
    for (const p of params) {
        const expr = p.expression?.expression ?? "null";
        const segments = p.name.split(".");
        let current = root;
        for (let i = 0; i < segments.length - 1; i++) {
            const seg = segments[i];
            if (typeof current[seg] !== "object" || current[seg] === null) current[seg] = {};
            current = current[seg] as Record<string, unknown>;
        }
        current[segments[segments.length - 1]] = expr;
    }
    return serializeSpelRecord(root);
}

function migrateKafkaParams(currentParameters: Parameter[], parameterDefinitions: Readonly<UIParameter[]>): Parameter[] | null {
    const oldValueParam = currentParameters.find((p) => p.name === "Value");
    const newValueDef = parameterDefinitions.find((def) => def.name === "Value");
    const newIndividualDefs = parameterDefinitions.filter((def) => !KAFKA_SYSTEM_PARAMS.has(def.name) && def.name !== "Value");

    const fallback = (def: UIParameter) => {
        const currentParam = currentParameters.find((p) => p.name === def.name);
        return currentParam ?? { name: def.name, expression: def.defaultValue };
    };

    // raw editor → dynamic: distribute Value record to individual params
    // Handles both old flat format { "combinedState.key": expr } and new nested { combinedState: { key: expr } }
    if (oldValueParam && newIndividualDefs.length > 0) {
        const knownParamNames = new Set(newIndividualDefs.map((d) => d.name));
        const parsed = flattenSpelRecord(oldValueParam.expression?.expression ?? "", knownParamNames);
        if (parsed) {
            return parameterDefinitions
                .filter((def) => !def.branchParam)
                .map((def) => {
                    if (!KAFKA_SYSTEM_PARAMS.has(def.name) && def.name !== "Value") {
                        const expr = parsed[def.name];
                        if (expr && expr !== "null") {
                            return { name: def.name, expression: { expression: expr, language: "spel" } };
                        }
                    }
                    return fallback(def);
                });
        }
    }

    // dynamic → raw editor: combine individual params into a nested Value record
    if (!oldValueParam && newValueDef) {
        const oldIndividualParams = currentParameters.filter((p) => !KAFKA_SYSTEM_PARAMS.has(p.name));
        if (oldIndividualParams.length > 0) {
            const record = buildNestedSpelRecord(oldIndividualParams);
            return parameterDefinitions
                .filter((def) => !def.branchParam)
                .map((def) => {
                    if (def.name === "Value") {
                        return { name: def.name, expression: { expression: record, language: "spel" } };
                    }
                    return fallback(def);
                });
        }
    }

    return null;
}

//We want to change parameters in node based on current node definition. This function can be used in
//two cases: dynamic parameters handling and automatic node migrations (e.g. in fragments)
export function adjustParameters(
    node: Readonly<NodeType>,
    parameterDefinitions: Readonly<UIParameter[]>,
    properties: ProcessAdditionalFields["properties"],
): NodeType {
    const path = parametersPath(node);

    if (!path || !parameterDefinitions) {
        return node;
    }

    let currentParameters;
    currentParameters = get(node, path);
    if (isRequestSource(node)) {
        currentParameters = currentParameters.concat(scenarioPropertiesToNodeProperties(properties));
    }

    // Kafka sink: migrate expressions when switching between raw editor and dynamic mode
    if (node.type === "Sink") {
        const ref = (node as unknown as { ref?: { typ?: string } }).ref;
        if (ref?.typ?.endsWith("kafka")) {
            const migrated = migrateKafkaParams(currentParameters, parameterDefinitions);
            if (migrated) {
                return setImmutable(node, path, migrated);
            }
        }
    }

    //TODO: currently dynamic branch parameters are *not* supported...
    const adjustedParameters = parameterDefinitions
        .filter((def) => !def.branchParam)
        .map((def) => {
            const currentParam = currentParameters.find((p) => p.name == def.name);
            const parameterFromDefinition = {
                name: def.name,
                expression: def.defaultValue,
            };
            return currentParam || parameterFromDefinition;
        });
    return setImmutable(node, path, adjustedParameters);
}
