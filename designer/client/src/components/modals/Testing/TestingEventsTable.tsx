// File: 'designer/client/src/components/modals/Testing/TestingEventsTable.tsx'
import { css, cx } from "@emotion/css";
import type { DataEditorProps, DataEditorRef, GridCell, GridColumn, GridSelection, Item, EditListItem } from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, GridCellKind, type CustomCell, type CustomRenderer, drawTextCell } from "@glideapps/glide-data-grid";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import moment from "moment";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { DTPicker } from "../../common/DTPicker";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import "@glideapps/glide-data-grid/dist/index.css";
import { nodeInput } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";

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
interface EventsTableProps {
    data?: TestingEventParameters[];
    onDataChange: (d: TestingEventParameters[]) => void;
    defaultEvent: TestingEventParameters;
    sourceOptions: string[];
    className?: string;
    sourceParameters: TestFormParameters[];
}

const emptySelection: GridSelection = { columns: CompactSelection.empty(), rows: CompactSelection.empty() };
const displayFormat = "YYYY-MM-DDTHH:mm:ss[Z]";

export const TestingEventsTable: React.FC<EventsTableProps> = ({ data = [], onDataChange, sourceOptions, className, defaultEvent }) => {
    const tableTheme = useTableTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const [hasFocus, setHasFocus] = useState(false);
    const ref = useRef<DataEditorRef>();
    const [cellMenuData, setCellMenuData] = useState<{ position: PopoverPosition | null; row?: number }>({ position: null });

    const tableColumns = useMemo<GridColumn[]>(
        () => [
            { id: "sourceId", title: "Source", width: 150, hasMenu: false },
            { id: "timestamp", title: "Timestamp", width: 200, hasMenu: false },
            { id: "variables", title: "Events", width: 300, grow: 1, hasMenu: false },
        ],
        [],
    );

    const sourceSelectRenderer = useMemo<CustomRenderer<SourceSelectCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isSourceSelectCell,
            draw: (args, cell) => {
                drawTextCell(args, cell.data.value, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: ({ value, onChange, target }) => (
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
                ),
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
                                // Use the unified display format; disable separate time format
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
                let display = rowData.variables || "";
                try {
                    if (rowData.variables) display = JSON.stringify(JSON.parse(rowData.variables));
                } catch (e) {
                    console.error(e.message);
                }
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

    const onCellsEdited: DataEditorProps["onCellsEdited"] = useCallback(
        (changes: readonly (EditListItem | { location: Item; value: SourceSelectCell | DateCell })[]) => {
            if (!changes.length) return;
            const rowUpdates: Record<number, TestingEventParameters> = {};
            changes.forEach(({ location, value }) => {
                const [col, row] = location;
                const base = rowUpdates[row] || { ...(data[row] || { sourceId: "", timestamp: "", variables: "" }) };
                const cellValue =
                    isSourceSelectCell(value) || isDateCell(value) ? value.data.value : (value as any).data?.toString?.() ?? "";
                if (col === 0) rowUpdates[row] = { ...base, sourceId: cellValue };
                else if (col === 1) rowUpdates[row] = { ...base, timestamp: cellValue };
                else if (col === 2) rowUpdates[row] = { ...base, variables: cellValue };
            });
            const maxRow = Math.max(...Object.keys(rowUpdates).map(Number));
            const next: TestingEventParameters[] = [];
            for (let r = 0; r < Math.max(data.length, maxRow + 1); r++) {
                if (rowUpdates[r]) next[r] = rowUpdates[r];
                else if (data[r]) next[r] = data[r];
                else next[r] = { sourceId: "", timestamp: "", variables: "" };
            }
            onDataChange(next);
        },
        [data, onDataChange],
    );

    const appendRow = useCallback(() => onDataChange([...data, { ...defaultEvent }]), [data, defaultEvent, onDataChange]);
    const deleteRows = useCallback((rows) => onDataChange(data.filter((_, i) => !rows.includes(i))), [data, onDataChange]);
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
    const overrideStyles = css({ "& .gdg-growing-entry": { minHeight: "100px !important" } });

    return (
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
                className={overrideStyles}
                columns={tableColumns}
                getCellContent={getCellContent}
                customRenderers={useMemo(() => [sourceSelectRenderer, dateRenderer], [sourceSelectRenderer, dateRenderer])}
                getCellsForSelection
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
                trailingRowOptions={{ sticky: true }}
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
                            deleteRows(idx);
                            clearSelection();
                            closeCellMenu();
                        }}
                    />
                )}
            </CellMenu>
        </Sizer>
    );
};
