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
import DataEditor, { CompactSelection, GridCellKind, type CustomRenderer, drawTextCell, TextCellEntry } from "@glideapps/glide-data-grid";
import type { ProvideEditorComponent } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import type { GridColumn } from "@glideapps/glide-data-grid/src/internal/data-grid/data-grid-types";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";
import { useTheme } from "@mui/material";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import { useErrorHighlights } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import type { VariablesCell } from "./CellContent";
import { getTestingCellContent, isSourceSelectCell, isVariablesCell } from "./CellContent";
import { DEFAULT_ROW_HEADER, drawFieldForDisplay } from "./drawText";
import type { SourceSelectCell } from "./SourceEditor";
import SourceEditor from "./SourceEditor";
import "@glideapps/glide-data-grid/dist/index.css";
import { buildDefaultVariablesMap, buildTestingRowUpdates, computeVariablesRowHeight } from "./utils";

export interface TestingDataRecords {
    sourceId: string;
    variables: string;
}

export interface TestingDataRecordsRequestData {
    sourceId: string;
    variables: unknown;
}

interface TableProps {
    data?: TestingDataRecords[];
    onRowUpdated: (rowIndex: number, row: TestingDataRecords) => void;
    onRowAdded: (rowIndex: number, row: TestingDataRecords) => void;
    onRowsDeleted: (deletedRows: number[]) => void;
    onRowMoved: (fromIndex: number, toIndex: number) => void;
    defaultDataRecord: TestingDataRecords;
    sourceOptions: string[];
    className?: string;
    sourceParameters: TestFormParameters[];
    cellErrors: CellError[];
    recordsToAddLimitExceeded?: boolean;
}

export const TABLE_HEIGHT = "65vh";
export const TABLE_WIDTH = "100%";
const TRAILING_ROW_HINT = "Add record";
const COLUMN_SOURCE_ID = "sourceId";
const COLUMN_VARIABLES_ID = "variables";
const COLUMN_SOURCE_TITLE = "Source";
const COLUMN_VARIABLES_TITLE = "Input variables";
const COLUMN_SOURCE_WIDTH = 150;
const COLUMN_VARIABLES_WIDTH = 300;

const emptySelection: GridSelection = { columns: CompactSelection.empty(), rows: CompactSelection.empty() };
const tableColumns: GridColumn[] = [
    { id: COLUMN_SOURCE_ID, title: COLUMN_SOURCE_TITLE, width: COLUMN_SOURCE_WIDTH, hasMenu: false },
    { id: COLUMN_VARIABLES_ID, title: COLUMN_VARIABLES_TITLE, width: COLUMN_VARIABLES_WIDTH, grow: 1, hasMenu: false },
];

type HeaderRenderArgs = { columnIndex: number; theme: Theme; rect: { width: number } };

export const Table: React.FC<TableProps> = ({
    data = [],
    onRowUpdated,
    onRowAdded,
    onRowsDeleted,
    onRowMoved,
    sourceOptions,
    className,
    defaultDataRecord,
    sourceParameters,
    cellErrors,
    recordsToAddLimitExceeded,
}) => {
    const tableTheme = useTableTheme();
    const theme = useTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const [hasFocus, setHasFocus] = useState(false);
    const ref = useRef<DataEditorRef | null>(null);
    const [cellMenuData, setCellMenuData] = useState<{ position: PopoverPosition | null; row?: number }>({ position: null });

    const { toggleTooltip, highlightRegions, drawCell, tooltipElement } = useErrorHighlights(cellErrors, ref);

    const sourceSelectRenderer = useMemo<CustomRenderer<SourceSelectCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isSourceSelectCell,
            draw: (drawArgs, cell) => {
                drawTextCell(drawArgs, cell.data.value, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: SourceEditor as ProvideEditorComponent<SourceSelectCell>,
                deletedValue: (sourceSelectCell) => ({
                    ...sourceSelectCell,
                    copyData: "",
                    data: { ...(sourceSelectCell as unknown as SourceSelectCell).data, value: "" },
                }),
            }),
        }),
        [],
    );

    const variablesRenderer = useMemo<CustomRenderer<VariablesCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isVariablesCell,
            draw: (drawArgs, cell) => {
                drawFieldForDisplay(drawArgs.ctx, cell.data.value, drawArgs.rect, drawArgs.theme, theme);
            },
            provideEditor: () => ({
                styleOverride: { padding: "4px" },
                editor: (props) => {
                    const { isHighlighted, onChange, value, validatedSelection } = props;

                    return (
                        <TextCellEntry
                            highlight={isHighlighted}
                            autoFocus={value.readonly !== true}
                            disabled={value.readonly === true}
                            altNewline={true}
                            value={value.data.value}
                            validatedSelection={validatedSelection}
                            onChange={(e) => {
                                const newVal = e.target.value;
                                onChange({
                                    ...value,
                                    copyData: newVal,
                                    data: { ...value.data, value: newVal },
                                });
                            }}
                        />
                    );
                },
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...(v as VariablesCell).data, value: "" } }),
            }),
        }),
        [theme],
    );

    const defaultVariablesBySourceId = useMemo(() => buildDefaultVariablesMap(sourceParameters), [sourceParameters]);

    const getCellContent = useCallback((item: Item): GridCell => getTestingCellContent(item, data, sourceOptions), [data, sourceOptions]);
    const buildRowUpdates = useCallback(
        (changes: readonly (EditListItem | { location: Item; value: SourceSelectCell })[]): Record<number, TestingDataRecords> =>
            buildTestingRowUpdates(changes, data, defaultVariablesBySourceId),
        [data, defaultVariablesBySourceId],
    );

    const onCellEdited = useCallback<NonNullable<DataEditorProps["onCellsEdited"]>>(
        (changes): void => {
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

    const onCellAdded = useCallback((): void => {
        const newRow: TestingDataRecords = { ...defaultDataRecord };
        const rowIndex = data.length;
        onRowAdded(rowIndex, newRow);
    }, [data.length, defaultDataRecord, onRowAdded]);

    const onCellDeleted = useCallback(
        (rows: number[]): number[] => {
            if (!rows.length) return [] as number[];
            const deletedRows = [...rows].sort((a, b) => a - b);
            onRowsDeleted(deletedRows);
            return deletedRows;
        },
        [onRowsDeleted],
    );

    const clearSelection = useCallback((): void => setSelection({ rows: CompactSelection.empty(), columns: CompactSelection.empty() }), []);
    const closeCellMenu = (): void => setCellMenuData((c) => ({ ...c, position: null }));
    const onDataEditorCellContextMenu = useCallback<NonNullable<DataEditorProps["onCellContextMenu"]>>(([, row], e): void => {
        e.preventDefault();
        setCellMenuData({ position: { top: e.bounds.y + e.localEventY, left: e.bounds.x + e.localEventX }, row });
    }, []);
    const getRowThemeOverride: GetRowThemeCallback = useCallback(
        (row): Partial<Theme> => ({ bgCell: row >= data.length ? tableTheme.bgCellMedium : tableTheme.bgCell }),
        [data.length, tableTheme.bgCell, tableTheme.bgCellMedium],
    );

    const handleRowReorder = useCallback(
        (fromIndex: number, toIndex: number): void => {
            const isDropAtFooter = toIndex === data.length;
            const isDropToTheSamePlace = fromIndex === toIndex;

            if (isDropAtFooter || isDropToTheSamePlace) return;

            onRowMoved(fromIndex, toIndex);
            clearSelection();
        },
        [data.length, onRowMoved, clearSelection],
    );

    // Track measured width of the variables column and the theme provided by the DataEditor
    const variablesColumnWidthRef = useRef<number | null>(null);
    const dataEditorThemeRef = useRef<Theme | null>(null);

    const getRowHeight = useCallback(
        (rowIndex: number): number => {
            if (rowIndex >= data.length || variablesColumnWidthRef.current == null || dataEditorThemeRef.current == null)
                return DEFAULT_ROW_HEADER;
            return computeVariablesRowHeight(
                data[rowIndex]?.variables || "",
                variablesColumnWidthRef.current,
                dataEditorThemeRef.current.lineHeight,
            );
        },
        [data],
    );

    const customRenderers = useMemo(() => [sourceSelectRenderer, variablesRenderer], [sourceSelectRenderer, variablesRenderer]);
    const trailingRowOptions = useMemo(() => ({ sticky: true, hint: TRAILING_ROW_HINT }), []);
    const highlightedRegions = useMemo(() => highlightRegions(), [highlightRegions]);
    const handleGridSelectionChange = useCallback(
        (selection: GridSelection): void => {
            setSelection(selection);
            toggleTooltip(selection);
        },
        [toggleTooltip],
    );
    const renderHeaderCell = useCallback((args: HeaderRenderArgs & { [key: string]: unknown }): void => {
        const { columnIndex, theme } = args;
        dataEditorThemeRef.current = theme;
        if (columnIndex === 1) {
            variablesColumnWidthRef.current = args.rect.width;
        }
        const col = tableColumns[columnIndex];
        if (!col) return;
        drawTextCell(args as unknown as BaseDrawArgs, col.title);
    }, []);
    const sizerSx = useMemo(() => ({ border: "1px solid", borderColor: tableTheme.borderColor }), [tableTheme.borderColor]);
    const deleteMenuIndexes = useMemo<number[]>(() => {
        if (selection.rows.toArray().length > 0) return selection.rows.toArray();
        if (selection.current?.range) {
            return Array.from({ length: selection.current.range.height }, (_, i) => selection.current.range.y + i);
        }
        return cellMenuData.row !== undefined && cellMenuData.row >= 0 ? [cellMenuData.row] : [];
    }, [selection, cellMenuData.row]);
    const handleDeleteRows = useCallback(
        (idx: number[]): void => {
            onCellDeleted(idx);
            clearSelection();
            closeCellMenu();
        },
        [onCellDeleted, clearSelection],
    );

    return (
        <>
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="data-records-table-container"
                className={className}
                sx={sizerSx}
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
                    customRenderers={customRenderers}
                    getCellsForSelection
                    onCellsEdited={onCellEdited}
                    onRowAppended={recordsToAddLimitExceeded ? undefined : onCellAdded}
                    rowMarkers="clickable-number"
                    rows={data.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width={TABLE_WIDTH}
                    height={TABLE_HEIGHT}
                    gridSelection={hasFocus ? selection : emptySelection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    trailingRowOptions={trailingRowOptions}
                    highlightRegions={highlightedRegions}
                    onGridSelectionChange={handleGridSelectionChange}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
                    drawHeader={renderHeaderCell}
                    rowHeight={getRowHeight}
                    onRowMoved={handleRowReorder}
                />
                <CellMenu anchorPosition={cellMenuData.position} onClose={closeCellMenu}>
                    {cellMenuData.row !== undefined && cellMenuData.row >= 0 && deleteMenuIndexes.length > 0 && (
                        <DeleteRowMenuItem indexes={deleteMenuIndexes} onClick={handleDeleteRows} />
                    )}
                </CellMenu>
            </Sizer>
            {tooltipElement}
        </>
    );
};
