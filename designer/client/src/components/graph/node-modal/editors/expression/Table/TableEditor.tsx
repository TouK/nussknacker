import { css } from "@emotion/css";
import DataEditor, {
    CompactSelection,
    DataEditorProps,
    DataEditorRef,
    GridCell,
    GridCellKind,
    GridColumn,
    GridSelection,
    GroupHeaderClickedEventArgs,
    HeaderClickedEventArgs,
    Item,
    Rectangle,
} from "@glideapps/glide-data-grid";
import { Box } from "@mui/material";
import { PopoverPosition } from "@mui/material/Popover/Popover";
import i18next from "i18next";
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ErrorBoundary from "../../../../../common/ErrorBoundary";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { useTypeOptions } from "../../../fragment-input-definition/FragmentInputDefinition";
import { EditorProps, ExtendedEditor } from "../Editor";
import "@glideapps/glide-data-grid/dist/index.css";
import { CellMenu, DeleteColumnMenuItem, DeleteRowMenuItem, ResetColumnWidthMenuItem } from "./CellMenu";
import { useErrorHighlights } from "./errorHighlights";
import { Sizer } from "./Sizer";
import { ActionTypes } from "./state/action";
import { longestRow } from "./state/helpers";
import { useTableState } from "./state/tableState";
import { useTableTheme } from "./tableTheme";
import { TypesMenu } from "./TypesMenu";
import { customRenderers } from "./customRenderers";
import { isDatePickerCell } from "./customCells";
import type { GetRowThemeCallback } from "@glideapps/glide-data-grid/src/internal/data-grid/render/data-grid-render.cells";

const SUPPORTED_TYPES = [
    "java.lang.String",
    "java.lang.Boolean",
    "java.lang.Double",
    "java.lang.Integer",
    "java.time.LocalDate",
    "java.time.LocalDateTime",
] as const;

export type SupportedType = (typeof SUPPORTED_TYPES)[number];

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
                    "&:hover, &:focus": {
                        backgroundColor: tableTheme.bgHeaderHovered,
                        color: tableTheme.bgIconHeader,
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
                <span>+</span>
            </button>
        </Box>
    );
};

const emptySelection = {
    columns: CompactSelection.empty(),
    rows: CompactSelection.empty(),
};

export const Table = ({ expressionObj, onValueChange, className, fieldErrors }: EditorProps) => {
    const tableDateContext = useTableState(expressionObj);
    const [{ rows, columns }, dispatch, rawExpression] = tableDateContext;

    useEffect(() => {
        if (rawExpression !== expressionObj.expression) {
            onValueChange(rawExpression);
        }
    }, [expressionObj.expression, onValueChange, rawExpression]);

    const { defaultTypeOption, orderedTypeOptions } = useTypeOptions<SupportedType>();
    const supportedTypes = useMemo(() => orderedTypeOptions.filter(({ value }) => SUPPORTED_TYPES.includes(value)), [orderedTypeOptions]);

    useEffect(() => {
        dispatch({
            type: ActionTypes.expand,
            rows: rows.length < 1 ? 1 : 0,
            columns: columns.length < 1 ? 1 : 0,
            dataType: defaultTypeOption.value,
        });
    }, [defaultTypeOption.value, dispatch, columns.length, rows.length]);

    const tableColumns = useMemo<GridColumn[]>(() => {
        return columns.map<GridColumn>(({ name, type = "", size }, i) => {
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
            const value = rows[row]?.[col];
            const column = columns[col];

            if (column.type === "java.time.LocalDateTime" || column.type === "java.time.LocalDate") {
                return {
                    kind: GridCellKind.Custom,
                    allowOverlay: true,
                    copyData: value ?? "",
                    data: {
                        kind: "date-picker-cell",
                        date: value ?? "",
                        format: column.type,
                    },
                };
            }

            return {
                kind: GridCellKind.Text,
                displayData: value ?? "",
                data: value ?? "",
                allowOverlay: true,
                readonly: false,
            };
        },
        [columns, rows],
    );

    const onCellsEdited: DataEditorProps["onCellsEdited"] = useCallback(
        (newValues) => {
            dispatch({
                type: ActionTypes.editData,
                dataChanges: newValues.map(({ location, value }) => ({
                    column: location[0],
                    row: location[1],
                    value: isDatePickerCell(value) ? value.data.date : value.data.toString(),
                })),
            });
        },
        [dispatch],
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

    const [selection, setSelection] = useState<GridSelection>(emptySelection);

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
            });

            return false;
        },
        [dispatch, defaultTypeOption.value],
    );

    const appendRow = useCallback(() => {
        dispatch({
            type: ActionTypes.expand,
            rows: 1,
            columns: 0,
        });
    }, [dispatch]);

    const overflowY = false;

    const [typesMenuData, setTypesMenuData] = React.useState<{
        position: PopoverPosition | null;
        column?: number;
    }>({ position: null });
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

    const [cellMenuData, setCellMenuData] = React.useState<{
        position: PopoverPosition | null;
        column?: number;
        row?: number;
    }>({
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
        setCellMenuData((current) => ({
            ...current,
            position: null,
        }));
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
    const { toggleTooltip, highlightRegions, drawCell, tooltipElement } = useErrorHighlights(fieldErrors, columns, ref);

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
        (dataType?: SupportedType) => {
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

    const [hasFocus, setHasFocus] = useState(false);

    const onColumnMoved = useCallback(
        (startIndex: number, endIndex: number) => {
            dispatch({
                type: ActionTypes.moveColumn,
                startIndex,
                endIndex,
            });
        },
        [dispatch],
    );

    const getRowThemeOverride: GetRowThemeCallback = useCallback(
        (row) => ({
            bgCell: row >= rows.length ? tableTheme.bgCellMedium : tableTheme.bgCell,
        }),
        [rows.length, tableTheme.bgCell, tableTheme.bgCellMedium],
    );

    return (
        <>
            <Sizer
                offsetParent={`[data-testid="window"] section`}
                overflowY={overflowY}
                data-testid="table-container"
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
                    customRenderers={customRenderers}
                    getRowThemeOverride={getRowThemeOverride}
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
                    rows={rows.length}
                    smoothScrollX
                    smoothScrollY
                    theme={tableTheme}
                    width="100%"
                    trailingRowOptions={trailingRowOptions}
                    gridSelection={hasFocus ? selection : emptySelection}
                    onGridSelectionChange={(selection) => {
                        setSelection(selection);
                        toggleTooltip(selection);
                    }}
                    onHeaderClicked={onDataEditorHeaderClicked}
                    onHeaderMenuClick={openTypeMenu}
                    onHeaderContextMenu={onHeaderContextMenu}
                    onGroupHeaderContextMenu={onHeaderContextMenu}
                    onCellContextMenu={onDataEditorCellContextMenu}
                    onColumnResize={onDataEditorColumnResize}
                    highlightRegions={highlightRegions()}
                    onItemHovered={toggleTooltip}
                    drawCell={drawCell}
                    onColumnMoved={onColumnMoved}
                />
                <TypesMenu
                    anchorPosition={typesMenuData?.position}
                    currentValue={columns[typesMenuData?.column]?.[1]}
                    onChange={onTypesMenuChange}
                    options={supportedTypes}
                />
                <CellMenu anchorPosition={cellMenuData?.position} onClose={closeCellMenu}>
                    {cellMenuData?.column >= 0 && cellMenuData?.row < 0 ? (
                        <ResetColumnWidthMenuItem
                            disabled={!columns[cellMenuData.column]?.size}
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
                            indexes={
                                selection.columns.toArray().length > 0
                                    ? selection.columns.toArray()
                                    : selection.current?.range
                                    ? Array.from({ length: selection.current.range.width }, (_, i) => selection.current.range.x + i)
                                    : [cellMenuData?.column]
                            }
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
                            indexes={
                                selection.rows.toArray().length > 0
                                    ? selection.rows.toArray()
                                    : selection.current?.range
                                    ? Array.from({ length: selection.current.range.height }, (_, i) => selection.current.range.y + i)
                                    : [cellMenuData?.row]
                            }
                            onClick={(indexes) => {
                                dispatch({
                                    type: ActionTypes.deleteRows,
                                    rows: indexes,
                                });
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                </CellMenu>
            </Sizer>
            {tooltipElement}
        </>
    );
};

export const TableEditor: ExtendedEditor = ({ className, ...props }: EditorProps) => {
    return (
        <Box className={className}>
            <Box display="flex">
                <ErrorBoundary>
                    <Table {...props} />
                </ErrorBoundary>
            </Box>
            {props.showValidation && <ValidationLabels fieldErrors={props.fieldErrors} />}
        </Box>
    );
};

TableEditor.isSwitchableTo = () => true; // TODO: implement
TableEditor.switchableToHint = () => i18next.t("editors.table.switchableToHint", "Switch to table mode");
TableEditor.notSwitchableToHint = () =>
    i18next.t("editors.table.notSwitchableToHint", "Expression must match schema to switch to table mode");
