import type { Item } from "@glideapps/glide-data-grid";
import { type EditListItem } from "@glideapps/glide-data-grid";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { isSourceSelectCell, isVariablesCell } from "./CellContent";
import { formatDataRecordsVariablesForDisplay, getRowLines, LINE_HEIGHT, paddingX, paddingY, SPLIT_SEPARATOR } from "./drawText";
import type { SourceSelectCell } from "./SourceEditor";
import type { TestingDataRecords } from "./Table";

export const mapInputDataRecordsToRunTestsFormat = (dataRecords: TestingDataRecords) => {
    let parsedVariables: unknown;
    try {
        parsedVariables = JSON.parse(dataRecords.variables);
    } catch {
        // Fallback: keep original string if not valid JSON
        parsedVariables = dataRecords.variables;
    }

    return {
        sourceId: dataRecords.sourceId,
        variables: parsedVariables,
    };
};

export const mapGeneratedTestingDataToTableFormat = (dataRecords: TestingDataRecords) => {
    dataRecords.variables = JSON.stringify(dataRecords.variables, null, 2);

    return dataRecords;
};

export function safeParseExpression<T = unknown>(expr: string): T {
    try {
        return JSON.parse(expr);
    } catch {
        throw new Error("Invalid JSON expression");
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
            defaultsBySourceId[sourceParameter.sourceId] = JSON.stringify(values, null, 2);
        } catch {
            // If stringify fails for any reason, fall back to empty string
            defaultsBySourceId[sourceParameter.sourceId] = "";
        }
    }

    return defaultsBySourceId;
}

export function computeVariablesRowHeight(variables: string, columnInnerWidth: number, themeLineHeight: number): number {
    const tokens = variables ? formatDataRecordsVariablesForDisplay(variables).split(SPLIT_SEPARATOR) : [];
    const rowLines = getRowLines(tokens, columnInnerWidth - paddingX);
    const linesCount = rowLines.length + 1;
    return linesCount * LINE_HEIGHT * themeLineHeight + paddingY;
}

export function buildInputDataRecordUpdates(
    changes: readonly (EditListItem | { location: Item; value: SourceSelectCell })[],
    data: TestingDataRecords[],
    defaultVariablesBySourceId: Record<string, string>,
): Record<number, TestingDataRecords> {
    const rowUpdates: Record<number, TestingDataRecords> = {};
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
