import type { CustomCell, GridCell, Item } from "@glideapps/glide-data-grid";
import { GridCellKind } from "@glideapps/glide-data-grid";

import type { TestingDataRecords } from "./Table";

type SourceSelectCellData = {
    kind: "source-select-cell";
    value: string;
    displayValue: string;
    options: string[];
    optionLabels: string[];
};
type SourceSelectCell = CustomCell<SourceSelectCellData>;
export const isSourceSelectCell = (c: GridCell): c is SourceSelectCell =>
    c.kind === GridCellKind.Custom && (c as SourceSelectCell).data?.kind === "source-select-cell";

type VariablesCellData = { kind: "variables-cell"; value: string; sourceId: string };
export type VariablesCell = CustomCell<VariablesCellData>;
export const isVariablesCell = (c: GridCell): c is VariablesCell =>
    c.kind === GridCellKind.Custom && (c as VariablesCell).data?.kind === "variables-cell";

export function getTestingCellContent(
    item: Item,
    data: TestingDataRecords[],
    sourceOptions: string[],
    sourceNameById: Record<string, string>,
): GridCell {
    const [columnIndex, rowIndex] = item;
    const rowData = data[rowIndex];
    if (!rowData) return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
    if (columnIndex === 0)
        return {
            kind: GridCellKind.Custom,
            allowOverlay: true,
            copyData: rowData.sourceId || "",
            data: {
                kind: "source-select-cell",
                value: rowData.sourceId || "",
                displayValue: (rowData.sourceId && sourceNameById[rowData.sourceId]) || rowData.sourceId || "",
                options: sourceOptions,
                optionLabels: sourceOptions.map((id) => sourceNameById[id] || id),
            },
            readonly: false,
        } as SourceSelectCell;
    if (columnIndex === 1) {
        const raw = rowData.variables || "";

        return {
            kind: GridCellKind.Custom,
            allowOverlay: true,
            copyData: rowData.variables || "",
            data: { kind: "variables-cell", value: raw, sourceId: rowData.sourceId },
            readonly: false,
        };
    }
    return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
}
