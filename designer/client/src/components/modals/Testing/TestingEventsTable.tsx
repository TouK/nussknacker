import type {
    DataEditorProps,
    DataEditorRef,
    GridCell,
    GridSelection,
    Item,
    EditListItem,
    BaseDrawArgs,
    Theme,
} from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, GridCellKind, type CustomCell, type CustomRenderer, drawTextCell } from "@glideapps/glide-data-grid";
import type { ProvideEditorComponent } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import type { GridColumn } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import { useErrorHighlights } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import {
    DEFAULT_ROW_HEADER,
    drawTextWithBoldSegments,
    formatEventVariablesForDisplay,
    getRowLines,
    LINE_HEIGHT,
    paddingX,
    paddingY,
    SPLIT_SEPARATOR,
} from "./drawText";
import TestingEventsTableSourceEditor from "./TestingEventsTableSourceEditor";
import "@glideapps/glide-data-grid/dist/index.css";
import { buildDefaultVariablesMap } from "./utils";

type SourceSelectCellData = { kind: "source-select-cell"; value: string; options: string[] };
type SourceSelectCell = CustomCell<SourceSelectCellData>;
const isSourceSelectCell = (c: GridCell): c is SourceSelectCell =>
    c.kind === GridCellKind.Custom && (c as SourceSelectCell).data?.kind === "source-select-cell";

type VariablesCellData = { kind: "variables-cell"; value: string; options: string[] };
type VariablesCell = CustomCell<VariablesCellData>;
const isVariablesCell = (c: GridCell): c is VariablesCell =>
    c.kind === GridCellKind.Custom && (c as VariablesCell).data?.kind === "variables-cell";

export interface TestingEventParameters {
    sourceId: string;
    variables: string;
}

export interface TestingEventParametersRequestData {
    sourceId: string;
    variables: unknown;
}

interface EventsTableProps {
    data?: TestingEventParameters[];
    onRowUpdated: (rowIndex: number, row: TestingEventParameters) => void;
    onRowAdded: (rowIndex: number, row: TestingEventParameters) => void;
    onRowsDeleted: (deletedRows: number[]) => void;
    onRowMoved: (fromIndex: number, toIndex: number) => void;
    defaultEvent: TestingEventParameters;
    sourceOptions: string[];
    className?: string;
    sourceParameters: TestFormParameters[];
    cellErrors: CellError[];
}

const emptySelection: GridSelection = { columns: CompactSelection.empty(), rows: CompactSelection.empty() };
const tableColumns: GridColumn[] = [
    { id: "sourceId", title: "Source", width: 150, hasMenu: false },
    { id: "variables", title: "Input variables", width: 300, grow: 1, hasMenu: false },
];

export const TestingEventsTable: React.FC<EventsTableProps> = ({
    data = [],
    onRowUpdated,
    onRowAdded,
    onRowsDeleted,
    onRowMoved,
    sourceOptions,
    className,
    defaultEvent,
    sourceParameters,
    cellErrors,
}) => {
    const tableTheme = useTableTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const [hasFocus, setHasFocus] = useState(false);
    const ref = useRef<DataEditorRef>();
    const [cellMenuData, setCellMenuData] = useState<{ position: PopoverPosition | null; row?: number }>({ position: null });

    const { toggleTooltip, highlightRegions, drawCell, tooltipElement } = useErrorHighlights(cellErrors, ref);

    const sourceSelectRenderer = useMemo<CustomRenderer<SourceSelectCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isSourceSelectCell,
            draw: (args, cell) => {
                drawTextCell(args, cell.data.value, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: TestingEventsTableSourceEditor as ProvideEditorComponent<SourceSelectCell>,
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...(v as unknown as SourceSelectCell).data, value: "" } }),
            }),
        }),
        [],
    );

    const variablesRenderer = useMemo<CustomRenderer<VariablesCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isVariablesCell,
            draw: (args, cell) => {
                drawTextWithBoldSegments(args.ctx, cell.data.value, args.rect, args.theme);
            },
            provideEditor: () => ({
                editor: () => <input />, // Simplified editor type
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...(v as VariablesCell).data, value: "" } }),
            }),
        }),
        [],
    );

    const defaultVariablesBySourceId = useMemo(() => buildDefaultVariablesMap(sourceParameters), [sourceParameters]);

    const getCellContent = useCallback(
        ([col, row]: Item): GridCell => {
            const rowData = data[row];
            if (!rowData) return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
            if (col === 0)
                return {
                    kind: GridCellKind.Custom,
                    allowOverlay: true,
                    copyData: rowData.sourceId || "",
                    data: { kind: "source-select-cell", value: rowData.sourceId || "", options: sourceOptions },
                    readonly: false,
                } as SourceSelectCell;
            if (col === 1) {
                const raw = rowData.variables || "";

                return {
                    kind: GridCellKind.Custom,
                    allowOverlay: true,
                    copyData: rowData.variables || "",
                    data: { kind: "variables-cell", value: raw, options: sourceOptions },
                    readonly: false,
                };
            }
            return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
        },
        [data, sourceOptions],
    );

    const buildRowUpdates = useCallback(
        (changes: readonly (EditListItem | { location: Item; value: SourceSelectCell })[]) => {
            const rowUpdates: Record<number, TestingEventParameters> = {};
            changes.forEach(({ location, value }) => {
                const [col, row] = location;
                const prevRow = data[row];
                const base = rowUpdates[row] || { ...(prevRow || { sourceId: "", timestamp: "", variables: "" }) };
                const cellValue = isSourceSelectCell(value) ? value.data.value : (value as any).data?.toString?.() ?? "";
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
        },
        [data, defaultVariablesBySourceId],
    );

    const onCellEdited = useCallback<NonNullable<DataEditorProps["onCellsEdited"]>>(
        (changes) => {
            if (!changes.length) return;
            const rowUpdates = buildRowUpdates(changes);
            if (!Object.keys(rowUpdates).length) return;
            Object.entries(rowUpdates).forEach(([rowIndexStr, value]) => {
                const rowIndex = Number(rowIndexStr);
                onRowUpdated(rowIndex, value);
            });
        },
        [buildRowUpdates, onRowUpdated],
    );

    const onCellAdded = useCallback(() => {
        const newRow: TestingEventParameters = { ...defaultEvent };
        const rowIndex = data.length;
        onRowAdded(rowIndex, newRow);
    }, [data.length, defaultEvent, onRowAdded]);

    const onCellDeleted = useCallback(
        (rows: number[]) => {
            if (!rows.length) return [] as number[];
            const deletedRows = [...rows].sort((a, b) => a - b);
            onRowsDeleted(deletedRows);
            return deletedRows;
        },
        [onRowsDeleted],
    );

    const clearSelection = useCallback(() => setSelection({ rows: CompactSelection.empty(), columns: CompactSelection.empty() }), []);
    const closeCellMenu = () => setCellMenuData((c) => ({ ...c, position: null }));
    const onDataEditorCellContextMenu = useCallback(([, row], e) => {
        e.preventDefault();
        setCellMenuData({ position: { top: e.bounds.y + e.localEventY, left: e.bounds.x + e.localEventX }, row });
    }, []);
    const getRowThemeOverride: GetRowThemeCallback = useCallback(
        (row) => ({ bgCell: row >= data.length ? tableTheme.bgCellMedium : tableTheme.bgCell }),
        [data.length, tableTheme.bgCell, tableTheme.bgCellMedium],
    );

    const handleRowReorder = useCallback(
        (fromIndex: number, toIndex: number) => {
            const isDropAtFooter = toIndex === data.length;
            const isDropToTheSamePlace = fromIndex === toIndex;

            if (isDropAtFooter || isDropToTheSamePlace) return;

            onRowMoved(fromIndex, toIndex);
            clearSelection();
        },
        [data.length, onRowMoved, clearSelection],
    );

    const getRowHeight = useCallback(
        (rowIndex: number) => {
            if (rowIndex >= data.length || !rowWidthRef?.current) return DEFAULT_ROW_HEADER;

            const rowLines = getRowLines(
                data[rowIndex]?.variables ? formatEventVariablesForDisplay(data[rowIndex].variables).split(SPLIT_SEPARATOR) : [],
                rowWidthRef.current - paddingX,
            );

            const linesCount = rowLines.length + 1;
            return linesCount * LINE_HEIGHT * dataEditorThemeRef.current.lineHeight + paddingY;
        },
        [data],
    );

    const rowWidthRef = useRef<number>(null);
    const dataEditorThemeRef = useRef<Theme>(null);
    return (
        <>
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="events-table-container"
                className={className}
                sx={{ border: "1px solid", borderColor: tableTheme.borderColor }}
                onFocus={() => setHasFocus(true)}
                onBlur={(e) => {
                    if (e.currentTarget.contains(e.relatedTarget)) return;
                    setHasFocus(false);
                }}
            >
                <DataEditor
                    ref={ref}
                    columns={tableColumns}
                    getCellContent={getCellContent}
                    customRenderers={useMemo(() => [sourceSelectRenderer, variablesRenderer], [sourceSelectRenderer, variablesRenderer])}
                    getCellsForSelection
                    onCellsEdited={onCellEdited}
                    onRowAppended={onCellAdded}
                    rowMarkers="clickable-number"
                    rows={data.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width="100%"
                    height={"65vh"}
                    gridSelection={hasFocus ? selection : emptySelection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    trailingRowOptions={{ sticky: true, hint: "Add record" }}
                    highlightRegions={highlightRegions()}
                    onGridSelectionChange={(selection) => {
                        setSelection(selection);
                        toggleTooltip(selection);
                    }}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
                    drawHeader={(args) => {
                        const { columnIndex, theme } = args;

                        dataEditorThemeRef.current = theme;
                        const isVariablesColumn = columnIndex === 1;

                        if (isVariablesColumn) {
                            rowWidthRef.current = args.rect.width;
                        }

                        const col = tableColumns[columnIndex];
                        if (!col) return undefined;

                        drawTextCell(args as unknown as BaseDrawArgs, col.title);

                        return undefined;
                    }}
                    rowHeight={getRowHeight}
                    onRowMoved={handleRowReorder}
                />
                <CellMenu anchorPosition={cellMenuData.position} onClose={closeCellMenu}>
                    {cellMenuData.row !== undefined && cellMenuData.row >= 0 && (
                        <DeleteRowMenuItem
                            indexes={
                                selection.rows.toArray().length > 0
                                    ? selection.rows.toArray()
                                    : selection.current?.range
                                    ? Array.from({ length: selection.current.range.height }, (_, i) => selection.current.range.y + i)
                                    : [cellMenuData.row]
                            }
                            onClick={(idx) => {
                                onCellDeleted(idx);
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    )}
                </CellMenu>
            </Sizer>
            {tooltipElement}
        </>
    );
};
