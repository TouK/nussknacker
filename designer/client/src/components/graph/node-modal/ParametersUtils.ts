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

/** Parse a top-level SpEL record `{ k1: expr1, k2: expr2 }` into a name→expression map. */
function parseSpelRecord(expression: string): Record<string, string> | null {
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
        if (name) result[name] = val;
    }
    return Object.keys(result).length > 0 ? result : null;
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
    if (oldValueParam && newIndividualDefs.length > 0) {
        const parsed = parseSpelRecord(oldValueParam.expression?.expression ?? "");
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

    // dynamic → raw editor: combine individual params into Value record
    if (!oldValueParam && newValueDef) {
        const oldIndividualParams = currentParameters.filter((p) => !KAFKA_SYSTEM_PARAMS.has(p.name));
        if (oldIndividualParams.length > 0) {
            const record = `{\n${oldIndividualParams.map((p) => `  ${p.name}: ${p.expression?.expression ?? "null"}`).join(",\n")}\n}`;
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
