import { SimpleEditor } from "../Editor";
import "@glideapps/glide-data-grid/dist/index.css";
import DataEditor, {
    CompactSelection,
    DataEditorProps,
    GridCell,
    GridCellKind,
    GridSelection,
    GroupHeaderClickedEventArgs,
    HeaderClickedEventArgs,
    Item,
    Rectangle,
} from "@glideapps/glide-data-grid";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GridColumn } from "@glideapps/glide-data-grid/dist/ts/data-grid/data-grid-types";
import ErrorBoundary from "../../../../../common/ErrorBoundary";
import { Sizer } from "./Sizer";
import { longestRow, transpose } from "./tableDataUtils";
import { css } from "@emotion/css";
import { useTypeOptions } from "../../../fragment-input-definition/FragmentInputDefinition";
import { PopoverPosition } from "@mui/material/Popover/Popover";
import { NuThemeProvider } from "../../../../../../containers/theme/nuThemeProvider";
import { DataEditorRef } from "@glideapps/glide-data-grid/dist/ts/data-editor/data-editor";
import { ActionTypes, useTableState } from "./tableState";
import { TypesMenu } from "./TypesMenu";
import { CellMenu, DeleteColumnMenuItem, DeleteRowMenuItem, ResetColumnWidthMenuItem } from "./CellMenu";
import { useTableTheme } from "./tableTheme";
import i18next from "i18next";
import { Box } from "@mui/material";

const SUPPORTED_TYPES = [
    "java.lang.String",
    "java.lang.Boolean",
    "java.lang.Double",
    "java.lang.Integer",
    "java.time.LocalDate",
    "java.time.LocalDateTime",
];

const RightElement = ({ onColumnAppend }: { onColumnAppend: () => void }) => {
    const tableTheme = useTableTheme();
    return (
        <Box
            sx={{
                display: "flex",
                flexDirection: "column",
                height: "100%",
                background: tableTheme.borderColor,
                "&>button": {
                    border: "none",
                    outline: "none",
                    height: "37px",
                    width: "37px",
                    fontSize: "24px",
                    backgroundColor: tableTheme.bgHeader,
                    color: tableTheme.textHeader,
                    borderBottom: `1px solid ${tableTheme.borderColor}`,
                    transition: "background-color 200ms",
                    cursor: "pointer",
                    "&:hover": {
                        backgroundColor: tableTheme.bgHeaderHovered,
                    },
                },
            }}
        >
            <button
                onClick={(event) => {
                    event.stopPropagation();
                    onColumnAppend();
                }}
            >
                +
            </button>
        </Box>
    );
};

export const TableEditor: SimpleEditor = ({ expressionObj, onValueChange }) => {
    const tableDateContext = useTableState(expressionObj);
    const [{ rows, columns }, dispatch, rawExpression] = tableDateContext;

    useEffect(() => {
        if (rawExpression !== expressionObj.expression) {
            onValueChange(rawExpression);
        }
    }, [expressionObj.expression, onValueChange, rawExpression]);

    const { defaultTypeOption, orderedTypeOptions } = useTypeOptions();
    const supportedTypes = useMemo(() => orderedTypeOptions.filter(({ value }) => SUPPORTED_TYPES.includes(value)), [orderedTypeOptions]);

    useEffect(() => {
        dispatch({
            type: ActionTypes.expand,
            rows: rows.length < 1 ? 1 : 0,
            columns: columns.length < 1 ? 1 : 0,
            dataType: defaultTypeOption.value,
        });
    }, [defaultTypeOption.value, dispatch, columns.length, rows.length]);

    const [additionalRows, hiddenAdditionalRows] = useMemo(() => {
        const HEADER_FIELDS_COUNT = 3; // name, type, size
        const columnsAsRows = columns.length ? transpose(columns, "") : [];
        const usedInHeaders = columnsAsRows.slice(0, HEADER_FIELDS_COUNT);
        const visible = columnsAsRows.slice(HEADER_FIELDS_COUNT);
        return [visible, usedInHeaders];
    }, [columns]);

    const tableRows = useMemo(() => [...additionalRows, ...rows], [additionalRows, rows]);

    const tableColumns = useMemo<GridColumn[]>(() => {
        return columns.map<GridColumn>(([name, type = "", size], i) => {
            const sizeValue = parseInt(size) || undefined;
            return {
                id: `${i}`,
                title: supportedTypes.find(({ value }) => value === type)?.label ?? type ?? "",
                group: name,
                hasMenu: true,
                width: sizeValue,
                grow: sizeValue ? undefined : 1,
            };
        });
    }, [columns, supportedTypes]);

    const tableTheme = useTableTheme();

    const getCellContent = useCallback(
        ([col, row]: Item): GridCell => {
            const value = tableRows[row]?.[col];
            return {
                kind: GridCellKind.Text,
                displayData: value ?? "",
                data: value ?? "",
                allowOverlay: true,
                readonly: false,
                themeOverride:
                    row < additionalRows.length
                        ? {
                              textDark: tableTheme.accentColor,
                          }
                        : null,
            };
        },
        [additionalRows.length, tableRows, tableTheme.accentColor],
    );

    const onCellsEdited: DataEditorProps["onCellsEdited"] = useCallback(
        (newValues) => {
            dispatch({
                type: ActionTypes.editData,
                dataChanges: newValues
                    .filter(({ location }) => location[1] >= additionalRows.length)
                    .map(({ location, value }) => ({
                        column: location[0],
                        row: location[1] - additionalRows.length,
                        value: value.data.toString(),
                    })),
                columnDataChanges: newValues
                    .filter(({ location }) => location[1] < additionalRows.length)
                    .map(({ location, value }) => ({
                        column: location[0],
                        index: location[1] + hiddenAdditionalRows.length,
                        value: value.data.toString(),
                    })),
            });
        },
        [additionalRows.length, dispatch, hiddenAdditionalRows.length],
    );

    const onGroupHeaderRenamed: DataEditorProps["onGroupHeaderRenamed"] = useCallback(
        (from, to) => {
            dispatch({
                type: ActionTypes.renameColumn,
                from,
                to,
            });
        },
        [dispatch],
    );

    const [selection, setSelection] = useState<GridSelection>({
        columns: CompactSelection.empty(),
        rows: CompactSelection.empty(),
    });

    const pasteWithExpand: DataEditorProps["onPaste"] = useCallback<(target: Item, values: readonly (readonly string[])[]) => boolean>(
        ([column, row], input) => {
            setSelection((current) => ({
                ...current,
                current: {
                    cell: [column, row],
                    range: {
                        x: column,
                        y: row,
                        width: longestRow(input).length,
                        height: input.length,
                    },
                    rangeStack: [],
                },
            }));

            dispatch({
                type: ActionTypes.insertData,
                row,
                column,
                input,
                dataType: defaultTypeOption.value,
                extraRowsCount: additionalRows.length,
            });

            return false;
        },
        [defaultTypeOption.value, additionalRows.length, dispatch],
    );

    const appendRow = useCallback(() => {
        dispatch({
            type: ActionTypes.expand,
            rows: 1,
            columns: 0,
        });
    }, [dispatch]);

    const overflowY = false;

    const [typesMenuData, setTypesMenuData] = React.useState<{ position: PopoverPosition | null; column?: number }>({ position: null });
    const openTypeMenu = useCallback((colIndex: number, bounds: Rectangle) => {
        const { x, y, width, height } = bounds;
        setTypesMenuData({
            position: {
                top: y + height / 6,
                left: x + width - height / 6,
            },
            column: colIndex,
        });
    }, []);

    const [cellMenuData, setCellMenuData] = React.useState<{ position: PopoverPosition | null; column?: number; row?: number }>({
        position: null,
    });
    const onHeaderContextMenu = useCallback((colIndex: number, event: HeaderClickedEventArgs | GroupHeaderClickedEventArgs) => {
        event.preventDefault();
        setCellMenuData({
            position: {
                top: event.bounds.y + event.localEventY,
                left: event.bounds.x + event.localEventX,
            },
            column: colIndex,
            row: -1,
        });
    }, []);

    const getGroupDetails: DataEditorProps["getGroupDetails"] = useCallback(
        (groupName) => ({
            name: groupName,
            overrideTheme: { headerFontStyle: "12px bold" },
        }),
        [],
    );

    const onColumnAppend = useCallback(() => {
        dispatch({
            type: ActionTypes.expand,
            rows: 0,
            columns: 1,
            dataType: defaultTypeOption.value,
        });
        ref.current.focus();
        setSelection(() => ({
            columns: CompactSelection.empty(),
            rows: CompactSelection.empty(),
            current: {
                cell: [columns.length, 0],
                range: {
                    x: columns.length,
                    y: 0,
                    width: 1,
                    height: 1,
                },
                rangeStack: [],
            },
        }));
    }, [columns.length, defaultTypeOption.value, dispatch]);

    const closeCellMenu = () => {
        setCellMenuData((current) => ({ ...current, position: null }));
    };

    const overrideGroupRenameInput = css({
        input: {
            borderRadius: 4,
            border: "1px solid var(--gdg-accent-color)",
            marginLeft: "calc(-1.25 * var(--gdg-cell-horizontal-padding))",
            padding: "calc(var(--gdg-cell-vertical-padding) * 3) var(--gdg-cell-horizontal-padding)",
            "&::selection": {
                background: "var(--gdg-accent-color)",
                color: "var(--gdg-accent-fg)",
            },
        },
    });

    const clearSelection = useCallback(() => {
        setSelection({
            rows: CompactSelection.empty(),
            columns: CompactSelection.empty(),
        });
    }, []);

    const ref = useRef<DataEditorRef>();

    const onDataEditorColumnResize = useCallback(
        (column, newSize, colIndex, newSizeWithGrow) => {
            dispatch({
                type: ActionTypes.columnResize,
                column: colIndex,
                size: newSizeWithGrow,
            });
        },
        [dispatch],
    );

    const onDataEditorCellContextMenu = useCallback(
        ([column, row], event) => {
            event.preventDefault();
            setCellMenuData({
                position: {
                    top: event.bounds.y + event.localEventY,
                    left: event.bounds.x + event.localEventX,
                },
                row,
                column,
            });
        },
        [setCellMenuData],
    );

    const onDataEditorHeaderClicked = useCallback(
        (colIndex, event) => {
            if (event.ctrlKey || event.shiftKey || event.metaKey) {
                return;
            }
            openTypeMenu(colIndex, event.bounds);
        },
        [openTypeMenu],
    );

    const onTypesMenuChange = useCallback(
        (dataType?: string) => {
            dispatch({
                type: ActionTypes.changeColumnType,
                column: typesMenuData?.column,
                dataType,
            });
            setTypesMenuData({ position: null });
        },
        [dispatch, typesMenuData?.column, setTypesMenuData],
    );

    const trailingRowOptions = useMemo<DataEditorProps["trailingRowOptions"]>(
        () => ({
            sticky: !overflowY,
        }),
        [overflowY],
    );

    const rightElement = useMemo<DataEditorProps["rightElement"]>(() => <RightElement onColumnAppend={onColumnAppend} />, [onColumnAppend]);

    return (
        <NuThemeProvider>
            <ErrorBoundary>
                <Sizer
                    overflowY={overflowY}
                    data-testid="table-container"
                    sx={{
                        border: "1px solid",
                        borderColor: tableTheme.borderColor,
                    }}
                >
                    <DataEditor
                        getRowThemeOverride={(row) => ({
                            bgCell: row >= tableRows.length ? tableTheme.bgCellMedium : tableTheme.bgCell,
                        })}
                        ref={ref}
                        className={overrideGroupRenameInput}
                        columns={tableColumns}
                        getCellContent={getCellContent}
                        getCellsForSelection={true}
                        onCellsEdited={onCellsEdited}
                        onGroupHeaderRenamed={onGroupHeaderRenamed}
                        getGroupDetails={getGroupDetails}
                        onPaste={pasteWithExpand}
                        onRowAppended={appendRow}
                        rightElement={rightElement}
                        rowMarkers="clickable-number"
                        columnSelect="multi"
                        rows={tableRows.length}
                        smoothScrollX
                        smoothScrollY
                        theme={tableTheme}
                        width="100%"
                        trailingRowOptions={trailingRowOptions}
                        gridSelection={selection}
                        onGridSelectionChange={setSelection}
                        onHeaderClicked={onDataEditorHeaderClicked}
                        onHeaderMenuClick={openTypeMenu}
                        onHeaderContextMenu={onHeaderContextMenu}
                        onGroupHeaderContextMenu={onHeaderContextMenu}
                        onCellContextMenu={onDataEditorCellContextMenu}
                        onColumnResize={onDataEditorColumnResize}
                    />
                </Sizer>
                <TypesMenu
                    anchorPosition={typesMenuData?.position}
                    currentValue={columns[typesMenuData?.column]?.[1]}
                    onChange={onTypesMenuChange}
                    options={supportedTypes}
                />
                <CellMenu anchorPosition={cellMenuData?.position} onClose={closeCellMenu}>
                    {cellMenuData?.column >= 0 && cellMenuData?.row < 0 ? (
                        <ResetColumnWidthMenuItem
                            disabled={!columns[cellMenuData.column][2]}
                            indexes={selection.columns.toArray().length > 0 ? selection.columns.toArray() : [cellMenuData?.column]}
                            onClick={(indexes) => {
                                dispatch({
                                    type: ActionTypes.resetColumnsSize,
                                    columns: indexes,
                                });
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                    {cellMenuData?.column >= 0 ? (
                        <DeleteColumnMenuItem
                            indexes={selection.columns.toArray().length > 0 ? selection.columns.toArray() : [cellMenuData?.column]}
                            onClick={(indexes) => {
                                dispatch({
                                    type: ActionTypes.deleteColumns,
                                    columns: indexes,
                                });
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                    {cellMenuData?.row >= 0 ? (
                        <DeleteRowMenuItem
                            indexes={selection.rows.toArray().length > 0 ? selection.rows.toArray() : [cellMenuData?.row]}
                            onClick={(indexes) => {
                                dispatch({
                                    type: ActionTypes.deleteRows,
                                    rows: indexes.map((i) => i - additionalRows.length),
                                    columnData: indexes.map((i) => i + hiddenAdditionalRows.length),
                                });
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                </CellMenu>
            </ErrorBoundary>
        </NuThemeProvider>
    );
};

TableEditor.isSwitchableTo = (expressionObj, editorConfig) => true;
TableEditor.switchableToHint = () => i18next.t("editors.table.switchableToHint", "Switch to table mode");
TableEditor.notSwitchableToHint = () =>
    i18next.t("editors.table.notSwitchableToHint", "Expression must match schema to switch to table mode");
