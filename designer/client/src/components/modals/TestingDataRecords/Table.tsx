import type {
    BaseDrawArgs,
    DataEditorProps,
    DataEditorRef,
    EditListItem,
    GetRowThemeCallback,
    GridCell,
    GridColumn,
    GridMouseCellEventArgs,
    GridSelection,
    Item,
    ProvideEditorComponent,
    Theme,
} from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, type CustomRenderer, drawTextCell, GridCellKind } from "@glideapps/glide-data-grid";
import { Box, useTheme } from "@mui/material";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { TestFormParameters } from "../../../common/TestResultUtils";
import type { NodeType, PropertiesType } from "../../../types/node";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { useErrorHighlights } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import type { VariablesCell } from "./CellContent";
import { getTestingCellContent, isSourceSelectCell, isVariablesCell } from "./CellContent";
import { DEFAULT_ROW_HEADER_HEIGHT, drawFieldForDisplay } from "./drawText";
import type { SourceSelectCell } from "./SourceEditor";
import { SourceEditor } from "./SourceEditor";
import "@glideapps/glide-data-grid/dist/index.css";
import { TableFooter } from "./TableFooter";
import type { TestingDataRecords } from "./types";
import { useTableHeight } from "./useTableHeight";
import { buildDefaultVariables, buildInputDataRecordUpdates, computeVariablesRowHeight } from "./utils";
import { VariablesEditor } from "./VariablesEditor";

export type { TestingDataRecords } from "./types";

interface TableProps {
    data?: TestingDataRecords[];
    onRowUpdated: (rowIndex: number, row: TestingDataRecords) => void;
    onRowAppended: () => void;
    onRowsDeleted: (deletedRows: number[]) => void;
    onRowMoved: (fromIndex: number, toIndex: number) => void;
    sourceParameters?: TestFormParameters;
    sourceOptions: string[];
    className?: string;
    cellErrors: CellError[];
    recordsToAddLimitExceeded?: boolean;
    node?: NodeType;
    processProperties?: PropertiesType;
}

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
    onRowAppended,
    onRowsDeleted,
    onRowMoved,
    sourceParameters,
    sourceOptions,
    className,
    cellErrors,
    recordsToAddLimitExceeded,
    node,
    processProperties,
}) => {
    const [draggingRow, setDraggingRow] = useState<number | null>(null);
    const lastDraggingRowRef = useRef<number | null>(null);
    const tableTheme = useTableTheme();
    const theme = useTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const ref = useRef<DataEditorRef | null>(null);
    const [cellMenuData, setCellMenuData] = useState<{ position: PopoverPosition | null; row?: number }>({ position: null });

    const { toggleTooltip, highlightRegions, drawCell, tooltipElement } = useErrorHighlights(cellErrors, ref);

    const sourceSelectRenderer = useMemo<CustomRenderer<SourceSelectCell>>(
        () => ({
            kind: GridCellKind.Custom,
            isMatch: isSourceSelectCell,
            draw: (drawArgs, cell) => {
                drawTextCell(drawArgs, cell.data.displayValue || cell.data.value, cell.contentAlign);
                return true;
            },
            provideEditor: () => ({
                editor: SourceEditor as ProvideEditorComponent<SourceSelectCell>,
                deletedValue: (sourceSelectCell) => ({
                    ...sourceSelectCell,
                    copyData: "",
                    data: { ...(sourceSelectCell as unknown as SourceSelectCell).data, value: "", displayValue: "" },
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
                    const { onChange, value } = props;

                    return <VariablesEditor value={value} onChange={onChange} node={node} processProperties={processProperties} />;
                },
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...(v as VariablesCell).data, value: "" } }),
            }),
        }),
        [theme, node, processProperties],
    );

    const defaultVariables = useMemo(() => buildDefaultVariables(sourceParameters), [sourceParameters]);

    const getCellContent = useCallback(
        (item: Item): GridCell => getTestingCellContent(item, data, sourceOptions, sourceParameters),
        [data, sourceOptions, sourceParameters],
    );

    const onCellEdited = useCallback<NonNullable<DataEditorProps["onCellsEdited"]>>(
        (changes): void => {
            if (!changes.length) return;
            const rowUpdates = buildInputDataRecordUpdates(changes, data, defaultVariables);
            if (!Object.keys(rowUpdates).length) return;
            Object.entries(rowUpdates).forEach(([rowIndexStr, value]) => {
                onRowUpdated(Number(rowIndexStr), value);
            });
        },
        [data, defaultVariables, onRowUpdated],
    );

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
        (row): Partial<Theme> => ({
            bgCell: draggingRow === row ? theme.palette.action.hover : row >= data.length ? tableTheme.bgCellMedium : tableTheme.bgCell,
        }),
        [data.length, draggingRow, tableTheme.bgCell, tableTheme.bgCellMedium, theme.palette.action.hover],
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
    const [variablesColumnWidth, setVariablesColumnWidth] = useState<number | null>(null);
    const dataEditorThemeRef = useRef<Theme | null>(null);

    const getRowHeight = useCallback(
        (rowIndex: number): number => {
            if (rowIndex >= data.length || variablesColumnWidth == null || dataEditorThemeRef.current == null)
                return DEFAULT_ROW_HEADER_HEIGHT;
            return computeVariablesRowHeight(data[rowIndex]?.variables || "", variablesColumnWidth, dataEditorThemeRef.current.lineHeight);
        },
        [data, variablesColumnWidth],
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
            setVariablesColumnWidth(args.rect.width);
        }
        const col = tableColumns[columnIndex];
        if (!col) return;
        drawTextCell(args as unknown as BaseDrawArgs, col.title);
    }, []);
    const { tableHeight } = useTableHeight(data, getRowHeight);
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

    const handleSetDraggingRow = useCallback(({ buttons, location }: GridMouseCellEventArgs) => {
        if (buttons > 0) {
            const row = location?.[1] ?? null;
            if (lastDraggingRowRef.current !== row) {
                lastDraggingRowRef.current = row;
                setDraggingRow(row);
            }
        } else {
            if (lastDraggingRowRef.current !== null) {
                lastDraggingRowRef.current = null;
                setDraggingRow(null);
            }
        }
    }, []);

    const rowsFromSelection = useMemo(() => selection.rows.toArray().sort((a, b) => a - b), [selection]);

    const handleRemoveSelectedRows = useCallback(() => {
        if (!onCellDeleted || rowsFromSelection.length === 0) return;
        onCellDeleted(rowsFromSelection);
        clearSelection();
    }, [clearSelection, onCellDeleted, rowsFromSelection]);

    const selectedRowsCount = rowsFromSelection.length;

    const onDeleteTableRow = useCallback(
        ({ current }: GridSelection) => {
            const currentCell = current?.cell;

            // Remove whole row when sourceId column value removed
            if (currentCell && tableColumns[currentCell[0]]?.id === COLUMN_SOURCE_ID) {
                onCellDeleted([currentCell[1]]);
                return false;
            }

            if (selectedRowsCount > 0) {
                handleRemoveSelectedRows();
                return false;
            }

            // keep native behaviour when no rows selected
            return true;
        },
        [handleRemoveSelectedRows, onCellDeleted, selectedRowsCount],
    );

    return (
        <Box
            sx={{
                "--sizer-height-cutout": "140px",
                display: "flex",
            }}
        >
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="data-records-table-container"
                className={className}
                sx={sizerSx}
                onBlur={(e) => {
                    if (e.currentTarget.contains(e.relatedTarget)) return;
                }}
            >
                <DataEditor
                    onDelete={onDeleteTableRow}
                    ref={ref}
                    columns={tableColumns}
                    getCellContent={getCellContent}
                    customRenderers={customRenderers}
                    getCellsForSelection
                    onCellsEdited={onCellEdited}
                    onRowAppended={recordsToAddLimitExceeded ? undefined : onRowAppended}
                    rowMarkers="both"
                    rows={data.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width="100%"
                    gridSelection={selection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    trailingRowOptions={trailingRowOptions}
                    highlightRegions={highlightedRegions}
                    onGridSelectionChange={handleGridSelectionChange}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
                    drawHeader={renderHeaderCell}
                    height={tableHeight}
                    rowHeight={getRowHeight}
                    onRowMoved={handleRowReorder}
                    rowSelectionMode="multi"
                    onMouseMove={handleSetDraggingRow}
                />
                <CellMenu anchorPosition={cellMenuData.position} onClose={closeCellMenu}>
                    {cellMenuData.row !== undefined && cellMenuData.row >= 0 && deleteMenuIndexes.length > 0 && (
                        <DeleteRowMenuItem indexes={deleteMenuIndexes} onClick={handleDeleteRows} />
                    )}
                </CellMenu>
                {selection.rows.length > 0 && (
                    <TableFooter
                        selectedCount={selectedRowsCount}
                        allRowsNumber={data.length}
                        handleRemoveRows={handleRemoveSelectedRows}
                    />
                )}
            </Sizer>
            {tooltipElement}
        </Box>
    );
};
