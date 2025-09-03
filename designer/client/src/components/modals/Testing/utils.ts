import _ from "lodash";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import type { TestingEventParameters } from "./TestingEventsTable";

export const mapEventsToRunTestsFormat = (event: TestingEventParameters) => {
    let parsedVariables: unknown;
    try {
        parsedVariables = JSON.parse(event.variables);
    } catch {
        // Fallback: keep original string if not valid JSON
        parsedVariables = event.variables;
    }

    return {
        sourceId: event.sourceId,
        variables: parsedVariables,
    };
};

export const mapGeneratedTestingDataToTableFormat = (events: TestingEventParameters) => {
    events.variables = JSON.stringify(events.variables, null, 2);

    return events;
};

export function safeParseExpression(expr: string): unknown {
    if (!expr) return "";
    try {
        return JSON.parse(expr);
    } catch {
        // not valid JSON -> return raw string
        return expr;
    }
}

export function buildDefaultVariablesMap(sourceParameters?: TestFormParameters[]): Record<string, string> {
    const defaultsBySourceId: Record<string, string> = {};
    if (!sourceParameters) return defaultsBySourceId;

    for (const sourceParameter of sourceParameters) {
        let values: unknown = {};
        const params = sourceParameter?.parameters ?? [];
        for (const param of params) {
            const expr = param?.defaultValue?.expression ?? "";
            values = safeParseExpression(expr);
        }

        try {
            defaultsBySourceId[sourceParameter.sourceId] = JSON.stringify(values);
        } catch {
            // If stringify fails for any reason, fall back to empty string
            defaultsBySourceId[sourceParameter.sourceId] = "";
        }
    }

    return defaultsBySourceId;
}

export const formatEventVariablesForDisplay = (raw?: string): string => {
    if (!raw) return "";
    if (typeof raw !== "string") return String(raw);

    try {
        const parsed = JSON.parse(raw);

        if (parsed === null || (!_.isPlainObject(parsed) && !Array.isArray(parsed))) {
            // primitive value
            return String(parsed);
        }

        const entries: string[] = [];

        const traverse = (value: unknown, path: string) => {
            if (_.isString(value) || typeof value === "number" || typeof value === "boolean" || value === null) {
                const key = path === "" ? "$" : path;
                entries.push(`${key}=${String(value)}`);
                return;
            }

            if (Array.isArray(value)) {
                value.forEach((v, idx) => {
                    const seg = path === "" ? `${idx}` : `${path}.${idx}`;
                    traverse(v, seg);
                });
                return;
            }

            if (_.isPlainObject(value)) {
                for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
                    const seg = path === "" ? k : `${path}.${k}`;
                    traverse(v, seg);
                }
                return;
            }

            // fallback: coerce to string
            const key = path === "" ? "$" : path;
            entries.push(`${key}=${String(value)}`);
        };

        traverse(parsed, "");

        if (entries.length === 0) return JSON.stringify(parsed);

        // sort entries by key (before the '=') alphabetically
        entries.sort((a, b) => {
            const ka = a.split("=")[0];
            const kb = b.split("=")[0];
            return ka.localeCompare(kb);
        });

        // join with ';' as requested
        return entries.join("; ");
    } catch {
        return raw.trim();
    }
};
