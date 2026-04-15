import type { CustomCell, GridCell, Item } from "@glideapps/glide-data-grid";
import { GridCellKind } from "@glideapps/glide-data-grid";

import type { TestingDataRecords } from "./types";

type VariablesCellData = { kind: "variables-cell"; value: string; sourceId: string };
export type VariablesCell = CustomCell<VariablesCellData>;
export const isVariablesCell = (c: GridCell): c is VariablesCell =>
    c.kind === GridCellKind.Custom && (c as VariablesCell).data?.kind === "variables-cell";

export function getTestingCellContent(item: Item, data: TestingDataRecords[]): GridCell {
    const [columnIndex, rowIndex] = item;
    const rowData = data[rowIndex];
    if (!rowData) return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
    if (columnIndex === 0) {
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
