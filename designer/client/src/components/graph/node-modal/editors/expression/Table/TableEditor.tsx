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
    Theme,
} from "@glideapps/glide-data-grid";
import React, { PropsWithChildren, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { GridColumn } from "@glideapps/glide-data-grid/dist/ts/data-grid/data-grid-types";
import ErrorBoundary from "../../../../../common/ErrorBoundary";
import { Sizer } from "./Sizer";
import { longestRow, transpose } from "./tableDataUtils";
import { css } from "@emotion/css";
import { ClickAwayListener, ListItemIcon, ListItemText, Menu, MenuItem, styled } from "@mui/material";
import { useTypeOptions } from "../../../fragment-input-definition/FragmentInputDefinition";
import { PopoverPosition } from "@mui/material/Popover/Popover";
import { NuThemeProvider } from "../../../../../../containers/theme/nuThemeProvider";
import { AutoAwesome, DeleteForever } from "@mui/icons-material";
import { DataEditorRef } from "@glideapps/glide-data-grid/dist/ts/data-editor/data-editor";
import { DataRow, useTableState } from "./tableState";

export const ColumnAddButton = styled("button")`
    border: none;
    outline: none;
    height: calc(37px);
    width: calc(37px);
    font-size: 24px;
    background-color: var(--gdg-bg-header);
    color: var(--gdg-text-header);
    border-bottom: 1px solid var(--gdg-border-color);
    transition: background-color 200ms;
    cursor: pointer;

    &:hover {
        background-color: var(--gdg-bg-header-hovered);
    }
`;

const darkTheme: Partial<Theme> = {
    accentColor: "#8c96ff",
    accentFg: "#16161b",
    accentLight: "rgba(202, 206, 255, 0.1)",

    textDark: "#ffffff",
    textMedium: "#b8b8b8",
    textLight: "#a0a0a0",
    textBubble: "#ffffff",

    bgIconHeader: "#b8b8b8",
    fgIconHeader: "#000000",
    textHeader: "#a1a1a1",
    textGroupHeader: "#a1a1a1",
    textHeaderSelected: "#000000",

    bgCell: "#16161b",
    bgCellMedium: "#202027",
    bgHeader: "#212121",
    bgHeaderHasFocus: "#212121",
    bgHeaderHovered: "#212121",

    bgBubble: "#212121",
    bgBubbleSelected: "#000000",

    bgSearchResult: "#423c24",

    borderColor: "rgba(225,225,225,0.2)",
    drilldownBorder: "rgba(225,225,225,0.4)",

    linkColor: "#4F5DFF",

    headerFontStyle: "bold 14px",
    baseFontStyle: "13px",
    fontFamily:
        "Inter, Roboto, -apple-system, BlinkMacSystemFont, avenir next, avenir, segoe ui, helvetica neue, helvetica, Ubuntu, noto, arial, sans-serif",
};

const SUPPORTED_TYPES = [
    "java.lang.String",
    "java.lang.Boolean",
    // numbers
    // "java.math.BigDecimal",
    // "java.math.BigInteger",
    "java.lang.Double",
    // "java.lang.Float",
    "java.lang.Integer",
    // "java.lang.Long",
    // "java.lang.Number",
    // "java.lang.Short",
    // dates
    "java.time.LocalDate",
    "java.time.LocalDateTime",
    // "java.time.LocalTime",
    // "java.time.Month",
    // "java.time.OffsetDateTime",
    // "java.time.OffsetTime",
    // "java.time.Period",
    // "java.time.ZoneId",
    // "java.time.ZoneOffset",
    // "java.time.ZonedDateTime",
];

interface TypesMenuParams {
    anchorPosition: PopoverPosition | null;
    currentValue: string;
    onChange: (value?: string) => void;
    options: {
        label: string;
        value: string;
    }[];
}

function TypesMenu({ anchorPosition, onChange, options, currentValue }: TypesMenuParams) {
    const open = useMemo(() => Boolean(anchorPosition), [anchorPosition]);
    return (
        <Menu
            anchorReference="anchorPosition"
            anchorPosition={anchorPosition}
            open={open}
            onClose={() => onChange()}
            transformOrigin={{
                vertical: "top",
                horizontal: "right",
            }}
            autoFocus
        >
            {options?.map(({ value, label }) => (
                <MenuItem
                    value={value}
                    key={value}
                    onClick={() => onChange(value)}
                    selected={currentValue === value}
                    autoFocus={currentValue === value}
                >
                    {label}
                </MenuItem>
            ))}
        </Menu>
    );
}

interface ColumnMenuParams {
    anchorPosition: PopoverPosition | null;
    onClose: () => void;
}

function CellMenu({ anchorPosition, onClose, children }: PropsWithChildren<ColumnMenuParams>) {
    const open = useMemo(() => {
        return Boolean(anchorPosition) && React.Children.toArray(children).length > 0;
    }, [anchorPosition, children]);

    return (
        <ClickAwayListener onClickAway={onClose}>
            <Menu
                sx={{
                    pointerEvents: "none",
                }}
                anchorReference="anchorPosition"
                anchorPosition={anchorPosition}
                open={open}
                onClose={onClose}
                PaperProps={{
                    sx: {
                        pointerEvents: "all",
                        backgroundColor: darkTheme.bgCell,
                    },
                }}
                MenuListProps={{
                    dense: true,
                }}
            >
                {children}
            </Menu>
        </ClickAwayListener>
    );
}

function DeleteRowMenuItem({ onClick, indexes = [] }: { indexes: number[]; onClick: (indexes: number[]) => void }) {
    return (
        <MenuItem
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <DeleteForever fontSize="small" />
            </ListItemIcon>
            <ListItemText>Remove {indexes.length > 1 ? "rows" : "row"}</ListItemText>
        </MenuItem>
    );
}

function ResetColumnWidthMenuItem({ indexes, onClick }: { indexes: number[]; onClick: (indexes: number[]) => void }) {
    return (
        <MenuItem
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <AutoAwesome fontSize="small" />
            </ListItemIcon>
            <ListItemText>Auto width</ListItemText>
        </MenuItem>
    );
}
function DeleteColumnMenuItem({ indexes, onClick }: { indexes: number[]; onClick: (indexes: number[]) => void }) {
    return (
        <MenuItem
            onClick={() => {
                onClick(indexes);
            }}
        >
            <ListItemIcon>
                <DeleteForever fontSize="small" />
            </ListItemIcon>
            <ListItemText>Remove {indexes.length > 1 ? "columns" : "column"}</ListItemText>
        </MenuItem>
    );
}

export const TableEditor: SimpleEditor = ({ expressionObj, onValueChange }) => {
    const [tableData, dispatch, rawExpression] = useTableState(expressionObj.expression);
    const { rows, columns } = tableData;

    useEffect(() => {
        if (rawExpression !== expressionObj.expression) {
            onValueChange(rawExpression);
        }
    }, [expressionObj.expression, onValueChange, rawExpression]);

    const { defaultTypeOption, orderedTypeOptions } = useTypeOptions();
    const supportedTypes = useMemo(() => orderedTypeOptions.filter(({ value }) => SUPPORTED_TYPES.includes(value)), [orderedTypeOptions]);

    useEffect(() => {
        dispatch({
            type: "expand",
            rows: tableData.rows.length < 1 ? 1 : 0,
            columns: tableData.columns.length < 1 ? 1 : 0,
            dataType: defaultTypeOption.value,
        });
    }, [defaultTypeOption.value, dispatch, tableData.columns.length, tableData.rows.length]);

    const [additionalRows, hiddenAdditionalRows] = useMemo<[DataRow[], DataRow[]]>(() => {
        const i = 3; // name, type, size
        const additional = columns.length ? transpose(columns, "") : [];
        const hidden = additional.slice(0, i);
        const visible = additional.slice(i);
        return [visible, hidden];
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
                              textDark: darkTheme.accentColor,
                          }
                        : null,
            };
        },
        [additionalRows.length, tableRows],
    );

    const onCellsEdited: DataEditorProps["onCellsEdited"] = useCallback(
        (newValues) => {
            dispatch({
                type: "edit-data",
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
            dispatch({ type: "rename-column", from, to });
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

            dispatch({ type: "insert-data", row, column, input, dataType: defaultTypeOption.value });

            return false;
        },
        [defaultTypeOption.value, dispatch],
    );

    const appendRow = useCallback(() => {
        dispatch({ type: "expand", rows: 1, columns: 0 });
    }, [dispatch]);

    const appendColumn = useCallback(() => {
        dispatch({ type: "expand", rows: 0, columns: 1, dataType: defaultTypeOption.value });
    }, [defaultTypeOption.value, dispatch]);

    const deleteColumns = useCallback(
        (columns: number[]) => {
            dispatch({ type: "delete-columns", columns });
        },
        [dispatch],
    );
    const deleteRows = useCallback(
        (indexes: number[]) => {
            dispatch({
                type: "delete-rows",
                rows: indexes.map((i) => i - additionalRows.length),
                columnData: indexes.map((i) => i + hiddenAdditionalRows.length),
            });
        },
        [additionalRows.length, dispatch, hiddenAdditionalRows.length],
    );

    const overflowY = false;

    const [typesMenuData, setTypesMenuData] = React.useState<{ position: PopoverPosition | null; column?: number }>({ position: null });
    const closeTypesMenu = (dataType?: string) => {
        setTypesMenuData(({ column }) => {
            dispatch({ type: "change-column-type", column, dataType });
            return { position: null };
        });
    };

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
        });
    }, []);

    const getGroupDetails: DataEditorProps["getGroupDetails"] = useCallback(
        (groupName) => ({
            name: groupName,
            overrideTheme: { headerFontStyle: "12px bold" },
        }),
        [],
    );

    const onColumnAppend = useCallback(
        (event: React.MouseEvent<HTMLButtonElement>) => {
            event.stopPropagation();
            appendColumn();
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
        },
        [appendColumn, columns.length],
    );

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
    return (
        <NuThemeProvider>
            <ErrorBoundary>
                <Sizer overflowY={overflowY}>
                    <DataEditor
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
                        rightElement={<ColumnAddButton onClick={onColumnAppend}>+</ColumnAddButton>}
                        rowMarkers="clickable-number"
                        columnSelect="multi"
                        rows={tableRows.length}
                        smoothScrollX
                        smoothScrollY
                        theme={darkTheme}
                        width="100%"
                        trailingRowOptions={{
                            tint: true,
                            sticky: !overflowY,
                        }}
                        gridSelection={selection}
                        onGridSelectionChange={setSelection}
                        onHeaderClicked={(colIndex, event) => {
                            if (event.ctrlKey || event.shiftKey || event.metaKey) {
                                return;
                            }
                            openTypeMenu(colIndex, event.bounds);
                        }}
                        onHeaderMenuClick={(colIndex, bounds) => openTypeMenu(colIndex, bounds)}
                        onHeaderContextMenu={onHeaderContextMenu}
                        onGroupHeaderContextMenu={onHeaderContextMenu}
                        onCellContextMenu={([column, row], event) => {
                            event.preventDefault();
                            setCellMenuData({
                                position: {
                                    top: event.bounds.y + event.localEventY,
                                    left: event.bounds.x + event.localEventX,
                                },
                                row,
                                column,
                            });
                        }}
                        onColumnResize={(column, newSize, colIndex, newSizeWithGrow) => {
                            dispatch({ type: "column-resize", column: colIndex, size: newSizeWithGrow });
                        }}
                    />
                </Sizer>
                <TypesMenu
                    anchorPosition={typesMenuData?.position}
                    currentValue={columns[typesMenuData?.column]?.[1]}
                    onChange={closeTypesMenu}
                    options={supportedTypes}
                />
                <CellMenu onClose={closeCellMenu} anchorPosition={cellMenuData?.position}>
                    {cellMenuData?.column >= 0 ? (
                        <ResetColumnWidthMenuItem
                            indexes={selection.columns.toArray().length > 0 ? selection.columns.toArray() : [cellMenuData?.column]}
                            onClick={(indexes) => {
                                dispatch({ type: "reset-columns-size", columns: indexes });
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                    {cellMenuData?.column >= 0 ? (
                        <DeleteColumnMenuItem
                            indexes={selection.columns.toArray().length > 0 ? selection.columns.toArray() : [cellMenuData?.column]}
                            onClick={(indexes) => {
                                deleteColumns(indexes);
                                clearSelection();
                                closeCellMenu();
                            }}
                        />
                    ) : null}
                    {cellMenuData?.row >= 0 ? (
                        <DeleteRowMenuItem
                            indexes={selection.rows.toArray().length > 0 ? selection.rows.toArray() : [cellMenuData?.row]}
                            onClick={(indexes) => {
                                deleteRows(indexes);
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
TableEditor.switchableToHint = () => "";
TableEditor.notSwitchableToHint = () => "";
