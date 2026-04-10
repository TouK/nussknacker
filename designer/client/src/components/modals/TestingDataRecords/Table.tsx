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
    Theme,
} from "@glideapps/glide-data-grid";
import DataEditor, { CompactSelection, type CustomRenderer, drawTextCell, GridCellKind } from "@glideapps/glide-data-grid";
import { Box, useTheme } from "@mui/material";
import type { PopoverPosition } from "@mui/material/Popover/Popover";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { useSize } from "../../../containers/hooks/useSize";
import { CellMenu, DeleteRowMenuItem } from "../../graph/node-modal/editors/expression/Table/CellMenu";
import type { CellError } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { useErrorHighlights } from "../../graph/node-modal/editors/expression/Table/errorHighlights";
import { Sizer } from "../../graph/node-modal/editors/expression/Table/Sizer";
import { useTableTheme } from "../../graph/node-modal/editors/expression/Table/tableTheme";
import type { VariablesCell } from "./CellContent";
import { getTestingCellContent, isVariablesCell } from "./CellContent";
import { DEFAULT_ROW_HEADER_HEIGHT, drawFieldForDisplay } from "./drawText";
import "@glideapps/glide-data-grid/dist/index.css";
import { TableFooter } from "./TableFooter";
import type { TestingDataRecords } from "./types";
import { useTableHeight } from "./useTableHeight";
import { buildInputDataRecordUpdates, computeVariablesRowHeight } from "./utils";
import { VariablesEditor } from "./VariablesEditor";

interface TableProps {
    data?: TestingDataRecords[];
    onRowUpdated: (rowIndex: number, row: TestingDataRecords) => void;
    onRowsDeleted: (deletedRows: number[]) => void;
    onRowMoved: (fromIndex: number, toIndex: number) => void;
    defaultDataRecord: TestingDataRecords;
    className?: string;
    cellErrors: CellError[];
    toolbar?: React.ReactNode;
}

export const TABLE_HEIGHT = "100vh";
export const TABLE_WIDTH = "100%";
const COLUMN_VARIABLES_ID = "variables";
const COLUMN_VARIABLES_TITLE = "Records";

const emptySelection: GridSelection = { columns: CompactSelection.empty(), rows: CompactSelection.empty() };
const tableColumns: GridColumn[] = [{ id: COLUMN_VARIABLES_ID, title: COLUMN_VARIABLES_TITLE, grow: 1, hasMenu: false }];

type HeaderRenderArgs = { columnIndex: number; theme: Theme; rect: { width: number } };

export const Table: React.FC<TableProps> = ({
    data = [],
    onRowUpdated,
    onRowsDeleted,
    onRowMoved,
    className,
    defaultDataRecord,
    cellErrors,
    toolbar,
}) => {
    const [draggingRow, setDraggingRow] = useState<number | null>(null);
    const lastDraggingRowRef = useRef<number | null>(null);
    const tableTheme = useTableTheme();
    const theme = useTheme();
    const [selection, setSelection] = useState<GridSelection>(emptySelection);
    const ref = useRef<DataEditorRef | null>(null);
    const [cellMenuData, setCellMenuData] = useState<{ position: PopoverPosition | null; row?: number }>({ position: null });

    // Scroll to the last row when a new record is added
    const prevDataLengthRef = useRef(data.length);
    useEffect(() => {
        if (data.length > prevDataLengthRef.current) {
            ref.current?.scrollTo(0, data.length - 1, "vertical");
        }
        prevDataLengthRef.current = data.length;
    }, [data.length]);

    // Observe the modal section ancestor to compute available height for DataEditor
    const { observe: observeSection, height: sectionHeight } = useSize();
    const outerBoxRef = useRef<HTMLDivElement>(null);
    useEffect(() => {
        let el: HTMLElement | null = outerBoxRef.current;
        while (el) {
            if (el.tagName === "SECTION" && el.closest('[data-testid="window"]')) {
                observeSection(el);
                return;
            }
            el = el.parentElement;
        }
    }, [observeSection]);
    const SECTION_CUTOUT = 220; // toolbar (inside table) + accordion header + GENERAL/TESTING tabs + test case row + padding
    const dataEditorHeight = sectionHeight > 0 ? Math.max(100, sectionHeight - SECTION_CUTOUT) : undefined;

    const { toggleTooltip, highlightRegions, drawCell, tooltipElement } = useErrorHighlights(cellErrors, ref);

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

                    return <VariablesEditor value={value} onChange={onChange} />;
                },
                deletedValue: (v) => ({ ...v, copyData: "", data: { ...(v as VariablesCell).data, value: "" } }),
            }),
        }),
        [theme],
    );

    const getCellContent = useCallback((item: Item): GridCell => getTestingCellContent(item, data), [data]);
    const buildRowUpdates = useCallback(
        (changes: readonly EditListItem[]): Record<number, TestingDataRecords> => buildInputDataRecordUpdates(changes, data),
        [data],
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
    const selectAllRows = useCallback((): void => {
        setSelection({ rows: CompactSelection.fromSingleSelection([0, data.length]), columns: CompactSelection.empty() });
    }, [data.length]);
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

    const customRenderers = useMemo(() => [variablesRenderer], [variablesRenderer]);
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
        if (columnIndex === 0) {
            setVariablesColumnWidth(args.rect.width);
        }
        const col = tableColumns[columnIndex];
        if (!col) return;
        drawTextCell(args as unknown as BaseDrawArgs, col.title);
    }, []);
    const { tableHeight } = useTableHeight(data, getRowHeight);

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

    const rowsFromSelection = useMemo(() => {
        if (!selection) return [];
        const rowsArr = selection.rows && typeof selection.rows.toArray === "function" ? selection.rows.toArray() : [];
        if (rowsArr.length > 0) return rowsArr.slice().sort((a, b) => a - b);

        return [];
    }, [selection]);

    const handleRemoveSelectedRows = useCallback(() => {
        if (!onCellDeleted || rowsFromSelection.length === 0) return;
        onCellDeleted(rowsFromSelection);
        clearSelection();
    }, [clearSelection, onCellDeleted, rowsFromSelection]);

    const selectedRowsCount = rowsFromSelection.length;

    const onDeleteTableRow = useCallback(
        (selection: GridSelection) => {
            if (selectedRowsCount > 0) {
                handleRemoveSelectedRows();
                return false;
            }

            // keep native behaviour when no rows selected
            return true;
        },
        [handleRemoveSelectedRows, selectedRowsCount],
    );

    return (
        <Box
            ref={outerBoxRef}
            sx={{
                display: "flex",
                flexDirection: "column",
                border: "1px solid",
                borderColor: tableTheme.borderColor,
            }}
        >
            {toolbar}
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={false}
                data-testid="data-records-table-container"
                className={className}
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
                    rowMarkers="both"
                    rows={data.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width={TABLE_WIDTH}
                    gridSelection={selection}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    getRowThemeOverride={getRowThemeOverride}
                    highlightRegions={highlightedRegions}
                    onGridSelectionChange={handleGridSelectionChange}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
                    drawHeader={renderHeaderCell}
                    height={dataEditorHeight ?? tableHeight}
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
            </Sizer>
            {selection.rows.length > 0 && (
                <TableFooter
                    selectedCount={selectedRowsCount}
                    allRowsNumber={data.length}
                    handleRemoveRows={handleRemoveSelectedRows}
                    handleSelectAll={selectedRowsCount < data.length ? selectAllRows : undefined}
                />
            )}
            {tooltipElement}
        </Box>
    );
};
