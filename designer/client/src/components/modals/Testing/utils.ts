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
        if (typeof parsed === "object" && parsed !== null) {
            return JSON.stringify(parsed);
        }
        return String(parsed);
    } catch {
        return raw.trim();
    }
};
