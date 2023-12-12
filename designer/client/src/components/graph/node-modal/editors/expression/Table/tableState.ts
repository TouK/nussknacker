import { Dispatch, useEffect, useMemo, useReducer, useState } from "react";
import { getNextColumnName, longestRow, parsers, stringifiers } from "./tableDataUtils";
import { TaggedUnion } from "type-fest";
import { ExpressionObj } from "../types";

export type DataColumn = string[];
export type DataRow = string[];

export type TableData = {
    columns: DataColumn[];
    rows: DataColumn[];
};

const emptyValue: TableData = {
    columns: [],
    rows: [],
};

export const ActionTypes = {
    expand: "expand",
    replaceData: "replace-data",
    editData: "edit-data",
    insertData: "insert-data",
    deleteRows: "delete-rows",
    deleteColumns: "delete-columns",
    resetColumnsSize: "reset-columns-size",
    columnResize: "column-resize",
    renameColumn: "rename-column",
    changeColumnType: "change-column-type",
} as const;

type Actions = {
    [ActionTypes.replaceData]: {
        data: TableData;
    };
    [ActionTypes.expand]: {
        rows: number;
        columns: number;
        dataType?: string;
    };
    [ActionTypes.editData]: {
        dataChanges: {
            row: number;
            column: number;
            value: string;
        }[];
        columnDataChanges?: {
            column: number;
            index: number;
            value: string;
        }[];
    };
    [ActionTypes.insertData]: {
        column: number;
        row: number;
        input: readonly (readonly string[])[];
        dataType?: string;
        extraRowsCount?: number;
    };
    [ActionTypes.deleteRows]: {
        rows: number[];
        columnData?: number[];
    };
    [ActionTypes.deleteColumns]: {
        columns: number[];
    };
    [ActionTypes.resetColumnsSize]: {
        columns: number[];
    };
    [ActionTypes.columnResize]: {
        column: number;
        size: number;
    };
    [ActionTypes.renameColumn]: {
        from: string;
        to: string;
    };
    [ActionTypes.changeColumnType]: {
        column: number;
        dataType: string;
    };
};

type Action = TaggedUnion<"type", Actions>;

export function expandTable(state: TableData, rowsNum: number, colsNum: number, dataType?: string) {
    if (colsNum || rowsNum) {
        const newCols = Array.from({ length: colsNum }, (_, i) => [
            getNextColumnName(
                state.columns.map(([c]) => c),
                state.columns.length + i,
            ),
            dataType,
        ]);
        const newRows = Array.from({ length: rowsNum }, () => []);
        return {
            columns: [...state.columns, ...newCols],
            rows: [...state.rows, ...newRows],
        };
    }
    return state;
}

function reducer(state: TableData, action: Action): TableData {
    switch (action.type) {
        case ActionTypes.insertData: {
            const longestColumnLength = longestRow(state.columns).length;
            const extraRowsCount = action.extraRowsCount || 0;
            const newRowsNeeded = action.row + action.input.length - (extraRowsCount + state.rows.length);
            const newColsNeeded = action.column + longestRow(action.input).length - state.columns.length;
            const updatedData = expandTable(state, newRowsNeeded, newColsNeeded, action.dataType);

            action.input.forEach((columns, y) => {
                columns.forEach((value, x) => {
                    const currentRow = action.row + y - extraRowsCount;
                    if (currentRow >= 0) {
                        updatedData.rows[currentRow][action.column + x] = value;
                    } else {
                        updatedData.columns[action.column + x][longestColumnLength + currentRow] = value;
                    }
                });
            });

            return updatedData;
        }
        case ActionTypes.editData:
            return {
                ...state,
                rows: action.dataChanges.reduce((rows, { row, column, value }) => {
                    rows[row] = rows[row] || [];
                    rows[row][column] = value;
                    return [...rows];
                }, state.rows),
                columns: action.columnDataChanges.reduce((columns, { column, index, value }) => {
                    columns[column] = columns[column] || [];
                    columns[column][index] = value;
                    return [...columns];
                }, state.columns),
            };
        case ActionTypes.renameColumn:
            // prevent duplicates
            if (state.columns.find(([name]) => name === action.to)) {
                return state;
            }
            return {
                ...state,
                columns: state.columns.map(([name, ...col]) => [name === action.from ? action.to : name, ...col]),
            };
        case ActionTypes.deleteColumns:
            return {
                ...state,
                columns: state.columns.filter((_, i) => !action.columns.includes(i)),
                rows: state.rows.map((r) => r.filter((_, i) => !action.columns.includes(i))),
            };
        case ActionTypes.deleteRows:
            return {
                ...state,
                rows: state.rows.filter((_, i) => !action.rows.includes(i)),
                columns: state.columns.map((r) => r.filter((_, i) => !action.columnData?.includes(i))),
            };
        case ActionTypes.changeColumnType:
            if (!(action.column >= 0 && action.dataType)) {
                return state;
            }
            return {
                ...state,
                columns: state.columns.map((col, i) => {
                    if (i !== action.column) {
                        return col;
                    }
                    const [name, _, ...rest] = col;
                    return [name, action.dataType, ...rest];
                }),
            };
        case ActionTypes.columnResize:
            return {
                ...state,
                columns: state.columns.map?.((column, i) => {
                    if (i !== action.column) {
                        return column;
                    }
                    const [name, type, , ...col] = column;
                    return [name, type, action.size.toString(), ...col];
                }),
            };
        case ActionTypes.resetColumnsSize:
            return {
                ...state,
                columns: state.columns.map((c, i) => (!action.columns.includes(i) ? c : [...c.slice(0, 2), null, ...c.slice(3)])),
            };
        case ActionTypes.expand:
            return expandTable(state, action.rows, action.columns, action.dataType);
        case ActionTypes.replaceData:
            return action.data;
        default:
            return state;
    }
}

export function useTableState({ expression, language }: ExpressionObj): [TableData, Dispatch<Action>, string] {
    const [rawExpression, setRawExpression] = useState<string>(expression);

    const fromExpression = useMemo(() => parsers[language], [language]);
    const toExpression = useMemo(() => stringifiers[language], [language]);

    const [state, dispatch] = useReducer(reducer, emptyValue, (defaultValue) => fromExpression(expression, defaultValue));

    useEffect(() => {
        setRawExpression((current) => {
            if (current === expression) {
                return current;
            }
            dispatch({
                type: ActionTypes.replaceData,
                data: fromExpression(expression, emptyValue),
            });
            return expression;
        });
    }, [expression, fromExpression]);

    useEffect(() => {
        setRawExpression(toExpression(state));
    }, [state, setRawExpression, toExpression]);

    return [state, dispatch, rawExpression];
}
