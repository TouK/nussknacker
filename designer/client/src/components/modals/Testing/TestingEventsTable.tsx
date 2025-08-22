import { css } from "@emotion/css";
import type { DataEditorProps, DataEditorRef, GridCell, GridColumn, GridSelection, Item, EditListItem } from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, GridCellKind, type CustomCell, type CustomRenderer, drawTextCell } from "@glideapps/glide-data-grid";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import "@glideapps/glide-data-grid/dist/index.css";

type SourceSelectCellData = { kind: "source-select-cell"; value: string; options: string[] };
type SourceSelectCell = CustomCell<SourceSelectCellData>;
const isSourceSelectCell = (cell: GridCell): cell is SourceSelectCell =>
    cell.kind === GridCellKind.Custom && (cell as SourceSelectCell).data?.kind === "source-select-cell";

export interface TestingEventParameters {
    sourceId: string;
    timestamp?: string; // allow undefined during creation
    variables: string; // JSON string
}

interface EventsTableProps {
    data?: TestingEventParameters[];
    onDataChange: (data: TestingEventParameters[]) => void;
    defaultEvent: TestingEventParameters;
    sourceOptions: string[];
    className?: string;
    sourceParameters: TestFormParameters[];
}

const emptySelection = {
    columns: CompactSelection.empty(),
    rows: CompactSelection.empty(),
};

export const TestingEventsTable: React.FC<EventsTableProps> = ({ data = [], onDataChange, sourceOptions, className, defaultEvent }) => {
    const tableTheme = useTableTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const [hasFocus, setHasFocus] = useState(false);
    const ref = useRef<DataEditorRef>();

    const [cellMenuData, setCellMenuData] = React.useState<{
        position: PopoverPosition | null;
        row?: number;
    }>({
        position: null,
    });

    // Define fixed columns for the events table
    const tableColumns = useMemo<GridColumn[]>(
        () => [
            {
                id: "sourceId",
                title: "Source",
                width: 150,
                hasMenu: false,
            },
            {
                id: "timestamp",
                title: "Timestamp",
                width: 200,
                hasMenu: false,
            },
            {
                id: "variables",
                title: "Events",
                width: 300,
                grow: 1,
                hasMenu: false,
            },
        ],
        [],
    );

    // lazy define renderer to avoid recreating
    const sourceSelectRenderer = useMemo<CustomRenderer<SourceSelectCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isSourceSelectCell,
            draw: (args, cell) => {
                drawTextCell(args, cell.data.value, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: (p) => {
                    const { value, onChange, target } = p;
                    return (
                        <select
                            autoFocus
                            style={{
                                minWidth: target.width,
                                minHeight: target.height,
                                padding: 0,
                                outline: 0,
                                width: target.width,
                                height: target.height,
                                background: "transparent",
                            }}
                            value={value.data.value}
                            onChange={(e) =>
                                onChange({
                                    ...value,
                                    copyData: e.target.value,
                                    data: { ...value.data, value: e.target.value },
                                })
                            }
                        >
                            <option value="" />
                            {value.data.options.map((o) => (
                                <option key={o} value={o}>
                                    {o}
                                </option>
                            ))}
                        </select>
                    );
                },
                deletedValue: (v) => ({
                    ...v,
                    copyData: "",
                    data: { ...v.data, value: "" },
                }),
            }),
        }),
        [],
    );

    const getCellContent = useCallback(
        ([col, row]: Item): GridCell => {
            const rowData = data[row];
            if (!rowData) {
                return {
                    kind: GridCellKind.Text,
                    displayData: "",
                    data: "",
                    allowOverlay: true,
                    readonly: false,
                };
            }
            if (col === 0) {
                return {
                    kind: GridCellKind.Custom,
                    allowOverlay: true,
                    copyData: rowData.sourceId || "",
                    data: { kind: "source-select-cell", value: rowData.sourceId || "", options: sourceOptions },
                    readonly: false,
                } as SourceSelectCell;
            }
            switch (col) {
                case 1:
                    return {
                        kind: GridCellKind.Text,
                        displayData: rowData.timestamp || "",
                        data: rowData.timestamp || "",
                        allowOverlay: true,
                        readonly: false,
                    };
                case 2: {
                    let display = rowData.variables || "";
                    // Try to pretty print JSON; fall back to raw text if parse fails
                    try {
                        if (rowData.variables) {
                            const parsed = JSON.parse(rowData.variables);
                            display = JSON.stringify(parsed); // single-line to keep cell compact
                        }
                    } catch (_) {
                        // ignore parsing errors; treat as plain text
                    }
                    return {
                        kind: GridCellKind.Text,
                        displayData: display,
                        data: rowData.variables || "",
                        allowOverlay: true,
                        readonly: false,
                    };
                }
                default:
                    return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
            }
        },
        [data, sourceOptions],
    );

    const onCellsEdited: DataEditorProps["onCellsEdited"] = useCallback(
        (newValues: readonly (EditListItem | { location: Item; value: SourceSelectCell })[]) => {
            if (!newValues.length) return;
            // Build a map of row index -> updated row (immutable)
            const rowUpdates: Record<number, TestingEventParameters> = {};
            newValues.forEach(({ location, value }) => {
                const [col, row] = location;
                const base: TestingEventParameters = rowUpdates[row] || { ...data[row] } || { sourceId: "", timestamp: "", variables: "" };
                let cellValue: string;
                if (isSourceSelectCell(value)) {
                    cellValue = value.data.value;
                } else {
                    cellValue = value.data?.toString?.() ?? "";
                }
                switch (col) {
                    case 0:
                        rowUpdates[row] = { ...base, sourceId: cellValue };
                        break;
                    case 1:
                        rowUpdates[row] = { ...base, timestamp: cellValue };
                        break;
                    case 2:
                        rowUpdates[row] = { ...base, variables: cellValue };
                        break;
                    default:
                        rowUpdates[row] = base;
                }
            });
            const maxRow = Math.max(...Object.keys(rowUpdates).map(Number));
            const next: TestingEventParameters[] = [];
            for (let r = 0; r < Math.max(data.length, maxRow + 1); r++) {
                if (rowUpdates[r]) {
                    next[r] = rowUpdates[r];
                } else if (data[r]) {
                    next[r] = data[r];
                } else {
                    next[r] = { sourceId: "", timestamp: "", variables: "" };
                }
            }
            onDataChange(next);
        },
        [data, onDataChange],
    );

    const appendRow = useCallback(() => {
        // clone defaultEvent to avoid sharing the same object reference
        onDataChange([...data, { ...defaultEvent }]);
    }, [data, defaultEvent, onDataChange]);

    const deleteRows = useCallback(
        (rowIndexes: number[]) => {
            onDataChange(data.filter((_, index) => !rowIndexes.includes(index)));
        },
        [data, onDataChange],
    );

    const clearSelection = useCallback(() => {
        setSelection({
            rows: CompactSelection.empty(),
            columns: CompactSelection.empty(),
        });
    }, []);

    const closeCellMenu = () => {
        setCellMenuData((current) => ({
            ...current,
            position: null,
        }));
    };

    const onDataEditorCellContextMenu = useCallback(([, row], event) => {
        event.preventDefault();
        setCellMenuData({
            position: { top: event.bounds.y + event.localEventY, left: event.bounds.x + event.localEventX },
            row,
        });
    }, []);

    const getRowThemeOverride: GetRowThemeCallback = useCallback(
        (row) => ({
            bgCell: row >= data.length ? tableTheme.bgCellMedium : tableTheme.bgCell,
        }),
        [data.length, tableTheme.bgCell, tableTheme.bgCellMedium],
    );

    const overrideStyles = css({
        "& .gdg-growing-entry": {
            minHeight: "100px !important",
        },
    });

    return (
        <>
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="events-table-container"
                className={className}
                sx={{
                    border: "1px solid",
                    borderColor: tableTheme.borderColor,
                }}
                onFocus={() => setHasFocus(true)}
                onBlur={(e) => {
                    if (e.currentTarget.contains(e.relatedTarget)) {
                        return;
                    }
                    setHasFocus(false);
                }}
            >
                <DataEditor
                    ref={ref}
                    className={overrideStyles}
                    columns={tableColumns}
                    getCellContent={getCellContent}
                    customRenderers={useMemo(() => [sourceSelectRenderer], [sourceSelectRenderer])}
                    getCellsForSelection={true}
                    onCellsEdited={onCellsEdited}
                    onRowAppended={appendRow}
                    rowMarkers="clickable-number"
                    rows={Math.max(data.length, 1)}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width="100%"
                    gridSelection={hasFocus ? selection : emptySelection}
                    onGridSelectionChange={setSelection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    trailingRowOptions={{
                        sticky: true,
                    }}
                />
                <CellMenu anchorPosition={cellMenuData?.position} onClose={closeCellMenu}>
                    {cellMenuData?.row !== undefined && cellMenuData?.row >= 0 ? (
                        <DeleteRowMenuItem
                            indexes={
                                selection.rows.toArray().length > 0
                                    ? selection.rows.toArray()
                                    : selection.current?.range
                                    ? Array.from({ length: selection.current.range.height }, (_, i) => selection.current.range.y + i)
                                    : [cellMenuData?.row]
                            }
                            onClick={(indexes) => {
                                deleteRows(indexes);
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                </CellMenu>
            </Sizer>
        </>
    );
};
