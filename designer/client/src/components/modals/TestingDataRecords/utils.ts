import type { Item } from "@glideapps/glide-data-grid";
import { type EditListItem } from "@glideapps/glide-data-grid";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { isSourceSelectCell, isVariablesCell } from "./CellContent";
import { formatEventVariablesForDisplay, getRowLines, LINE_HEIGHT, paddingX, paddingY, SPLIT_SEPARATOR } from "./drawText";
import type { SourceSelectCell } from "./SourceEditor";
import type { TestingEventParameters } from "./Table";

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

export function computeVariablesRowHeight(variables: string, columnInnerWidth: number, themeLineHeight: number): number {
    const tokens = variables ? formatEventVariablesForDisplay(variables).split(SPLIT_SEPARATOR) : [];
    const rowLines = getRowLines(tokens, columnInnerWidth - paddingX);
    const linesCount = rowLines.length + 1;
    return linesCount * LINE_HEIGHT * themeLineHeight + paddingY;
}

export function buildTestingRowUpdates(
    changes: readonly (EditListItem | { location: Item; value: SourceSelectCell })[],
    data: TestingEventParameters[],
    defaultVariablesBySourceId: Record<string, string>,
): Record<number, TestingEventParameters> {
    const rowUpdates: Record<number, TestingEventParameters> = {};
    changes.forEach(({ location, value }) => {
        const [col, row] = location;
        const prevRow = data[row];
        const base = rowUpdates[row] || { ...(prevRow || { sourceId: "", timestamp: "", variables: "" }) };

        let cellValue: string;
        if (isSourceSelectCell(value)) {
            cellValue = value.data.value;
        } else if (isVariablesCell(value as any)) {
            cellValue = (value as any).data?.value ?? "";
        } else {
            const maybeData = (value as unknown as { data?: unknown }).data;
            if (typeof maybeData === "string") cellValue = maybeData;
            else if (maybeData != null) cellValue = String(maybeData);
            else cellValue = "";
        }

        if (col === 0) {
            if (prevRow?.sourceId !== cellValue) {
                const resetVars = cellValue ? defaultVariablesBySourceId[cellValue] ?? "" : "";
                rowUpdates[row] = { ...base, sourceId: cellValue, variables: resetVars };
            } else {
                rowUpdates[row] = { ...base, sourceId: cellValue };
            }
        } else if (col === 1) {
            rowUpdates[row] = { ...base, variables: cellValue };
        }
    });
    return rowUpdates;
}
