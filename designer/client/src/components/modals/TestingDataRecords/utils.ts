import type { Item } from "@glideapps/glide-data-grid";
import { type EditListItem } from "@glideapps/glide-data-grid";

import type { UIParameter } from "../../../types/definition";
import { isVariablesCell } from "./CellContent";
import { formatDataRecordsVariablesForDisplay, getRowLines, LINE_HEIGHT, paddingX, paddingY, SPLIT_SEPARATOR } from "./drawText";
import type { TestingDataRecords } from "./types";

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
        return undefined;
    }
}

export function buildDefaultVariables(parameters?: UIParameter[]): string {
    // Source test parameters always contain a single "Input variables"
    const expression = parameters?.[0]?.defaultValue?.expression ?? "";
    const values = safeParseExpression(expression);
    try {
        return values ? JSON.stringify(values, null, 2) : "";
    } catch {
        return "";
    }
}

export function computeVariablesRowHeight(variables: string, columnInnerWidth: number, themeLineHeight: number): number {
    const tokens = variables ? formatDataRecordsVariablesForDisplay(variables).split(SPLIT_SEPARATOR) : [];
    const rowLines = getRowLines(tokens, columnInnerWidth - paddingX);
    const linesCount = rowLines.length + 1;
    return linesCount * LINE_HEIGHT * themeLineHeight + paddingY;
}

export function buildInputDataRecordUpdates(
    changes: readonly EditListItem[],
    data: TestingDataRecords[],
): Record<number, TestingDataRecords> {
    const rowUpdates: Record<number, TestingDataRecords> = {};
    changes.forEach(({ location, value }) => {
        const [col, row] = location;
        const prevRow = data[row];
        const base = rowUpdates[row] || { ...(prevRow || { sourceId: "", timestamp: "", variables: "" }) };

        let cellValue: string;
        if (isVariablesCell(value as any)) {
            cellValue = (value as any).data?.value ?? "";
        } else {
            const maybeData = (value as unknown as { data?: unknown }).data;
            if (typeof maybeData === "string") cellValue = maybeData;
            else if (maybeData != null) cellValue = String(maybeData);
            else cellValue = "";
        }

        if (col === 0) {
            rowUpdates[row] = { ...base, variables: cellValue };
        }
    });
    return rowUpdates;
}
