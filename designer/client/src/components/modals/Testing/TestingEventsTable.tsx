import { css, cx } from "@emotion/css";
import type { DataEditorProps, DataEditorRef, GridCell, GridSelection, Item, EditListItem } from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, GridCellKind, type CustomCell, type CustomRenderer, drawTextCell } from "@glideapps/glide-data-grid";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import moment from "moment";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { DTPicker } from "../../common/DTPicker";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import { useErrorHighlights } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import { nodeInput } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import TestingEventsTableSourceEditor from "./TestingEventsTableSourceEditor";
import "@glideapps/glide-data-grid/dist/index.css";
import { buildDefaultVariablesMap, formatEventVariablesForDisplay } from "./utils";

type DateCellData = { kind: "date-cell"; value: string };
type DateCell = CustomCell<DateCellData>;
const isDateCell = (c: GridCell): c is DateCell => c.kind === GridCellKind.Custom && (c as DateCell).data?.kind === "date-cell";

type SourceSelectCellData = { kind: "source-select-cell"; value: string; options: string[] };
type SourceSelectCell = CustomCell<SourceSelectCellData>;
const isSourceSelectCell = (c: GridCell): c is SourceSelectCell =>
    c.kind === GridCellKind.Custom && (c as SourceSelectCell).data?.kind === "source-select-cell";

export interface TestingEventParameters {
    sourceId: string;
    timestamp?: string;
    variables: string;
}

export interface TestingEventParametersRequestData {
    sourceId: string;
    timestamp?: string;
    variables: Record<string, unknown>;
}

interface EventsTableProps {
    data?: TestingEventParameters[];
    onRowUpdated: (rowIndex: number, row: TestingEventParameters) => void;
    onRowAdded: (rowIndex: number, row: TestingEventParameters) => void;
    onRowsDeleted: (deletedRows: number[]) => void;
    defaultEvent: TestingEventParameters;
    sourceOptions: string[];
    className?: string;
    sourceParameters: TestFormParameters[];
    cellErrors: CellError[];
}

const emptySelection: GridSelection = { columns: CompactSelection.empty(), rows: CompactSelection.empty() };
const displayFormat = "YYYY-MM-DDTHH:mm:ss[Z]";
const tableColumns = [
    { id: "sourceId", title: "Source", width: 150, hasMenu: false },
    { id: "timestamp", title: "Timestamp", width: 200, hasMenu: false },
    { id: "variables", title: "Events", width: 300, grow: 1, hasMenu: false },
];

export const TestingEventsTable: React.FC<EventsTableProps> = ({
    data = [],
    onRowUpdated,
    onRowAdded,
    onRowsDeleted,
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
                editor: TestingEventsTableSourceEditor as any,
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...v.data, value: "" } }),
            }),
        }),
        [],
    );

    const dateRenderer = useMemo<CustomRenderer<DateCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isDateCell,
            draw: (args, cell) => {
                const m = moment(cell.data.value).utc();
                const text = m.isValid() ? m.format(displayFormat) : "";
                drawTextCell(args, text, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: ({ value, onChange, target }) => {
                    const mVal = value.data.value ? moment(value.data.value).utc() : undefined;
                    return (
                        <div
                            style={{
                                width: target.width,
                                height: target.height,
                                display: "flex",
                                alignItems: "center",
                            }}
                        >
                            <DTPicker
                                open={true}
                                value={mVal}
                                inputProps={{
                                    className: cx([nodeInput]),
                                }}
                                dateFormat={displayFormat}
                                timeFormat={false}
                                onChange={(m) => {
                                    const iso = moment.isMoment(m) && m.isValid() ? m.utc().toISOString() : "";
                                    onChange({
                                        ...value,
                                        copyData: iso,
                                        data: { ...value.data, value: iso },
                                    });
                                }}
                            />
                        </div>
                    );
                },
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...v.data, value: "" } }),
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
            if (col === 1)
                return {
                    kind: GridCellKind.Custom,
                    allowOverlay: true,
                    copyData: rowData.timestamp || "",
                    data: { kind: "date-cell", value: rowData.timestamp || "" },
                    readonly: false,
                } as DateCell;
            if (col === 2) {
                const raw = rowData.variables || "";
                const display = formatEventVariablesForDisplay(raw);

                return {
                    kind: GridCellKind.Text,
                    displayData: display,
                    data: rowData.variables || "",
                    allowOverlay: true,
                    readonly: false,
                };
            }
            return { kind: GridCellKind.Text, displayData: "", data: "", allowOverlay: true, readonly: false };
        },
        [data, sourceOptions],
    );

    const buildRowUpdates = useCallback(
        (changes: readonly (EditListItem | { location: Item; value: SourceSelectCell | DateCell })[]) => {
            const rowUpdates: Record<number, TestingEventParameters> = {};
            changes.forEach(({ location, value }) => {
                const [col, row] = location;
                const prevRow = data[row];
                const base = rowUpdates[row] || { ...(prevRow || { sourceId: "", timestamp: "", variables: "" }) };
                const cellValue =
                    isSourceSelectCell(value) || isDateCell(value) ? value.data.value : (value as any).data?.toString?.() ?? "";
                if (col === 0) {
                    if (prevRow?.sourceId !== cellValue) {
                        const resetVars = cellValue ? defaultVariablesBySourceId[cellValue] ?? "" : "";
                        rowUpdates[row] = { ...base, sourceId: cellValue, variables: resetVars };
                    } else {
                        rowUpdates[row] = { ...base, sourceId: cellValue };
                    }
                } else if (col === 1) {
                    rowUpdates[row] = { ...base, timestamp: cellValue };
                } else if (col === 2) {
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

    return (
        <>
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="events-table-container"
                className={className}
                sx={{ border: "1px solid", borderColor: tableTheme.borderColor, minHeight: "60px", ml: 1 }}
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
                    customRenderers={useMemo(() => [sourceSelectRenderer, dateRenderer], [sourceSelectRenderer, dateRenderer])}
                    getCellsForSelection
                    onCellsEdited={onCellEdited}
                    onRowAppended={onCellAdded}
                    rowMarkers="clickable-number"
                    rows={data.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width="100%"
                    gridSelection={hasFocus ? selection : emptySelection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    trailingRowOptions={{ sticky: true, hint: "Add row" }}
                    highlightRegions={highlightRegions()}
                    onGridSelectionChange={(selection) => {
                        setSelection(selection);
                        toggleTooltip(selection);
                    }}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
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
