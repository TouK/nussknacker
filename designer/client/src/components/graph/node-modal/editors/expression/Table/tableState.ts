import { Dispatch, useEffect, useMemo, useReducer, useState } from "react";
import { getNextColumnName, getParser, getStringifier, longestRow } from "./tableDataUtils";
import { TaggedUnion } from "type-fest";
import { ExpressionObj } from "../types";

export type DataColumn = {
    name: string;
    type: string;
    size?: string;
};
export type DataRow = string[];

export type TableData = {
    columns: DataColumn[];
    rows: DataRow[];
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

function expandArray<T>(array: T[], length: number, fill?: (i: number) => T): T[] {
    if (length <= 0) {
        return array;
    }
    const newValues = Array.from({ length }, (_, i: number) => fill?.(array.length + i));
    return [...array, ...newValues];
}

export function expandTable(state: TableData, rowsNum = 0, colsNum = 0, dataType?: string): TableData {
    if (rowsNum <= 0 && colsNum <= 0) {
        return state;
    }

    const currentNames = state.columns.map(({ name }) => name);
    const columns = expandArray(state.columns, colsNum, (i) => {
        return {
            name: getNextColumnName(currentNames, i),
            type: dataType,
        };
    });

    const rows = expandArray(state.rows, rowsNum).map((r = []) => {
        return expandArray(r, columns.length - r.length, () => null);
    });

    return { columns, rows };
}

function reducer(state: TableData, action: Action): TableData {
    switch (action.type) {
        case ActionTypes.insertData: {
            const newRowsNeeded = action.row + action.input.length - state.rows.length;
            const newColsNeeded = action.column + longestRow(action.input).length - state.columns.length;
            const updatedData = expandTable(state, newRowsNeeded, newColsNeeded, action.dataType);

            const rows = updatedData.rows.slice();
            action.input.forEach((columns, y) => {
                columns.forEach((value, x) => {
                    const currentRow = action.row + y;
                    if (currentRow >= 0) {
                        const row = rows[currentRow].slice();
                        row[action.column + x] = value;
                        rows[currentRow] = row;
                    }
                });
            });

            return { ...updatedData, rows };
        }
        case ActionTypes.editData:
            return {
                ...state,
                rows: action.dataChanges.reduce(
                    (rows, { row: y, column: x, value }) => rows.map((r, i) => (i === y ? r.map((v, i) => (i === x ? value : v)) : r)),
                    state.rows,
                ),
            };
        case ActionTypes.renameColumn:
            // prevent duplicates
            if (state.columns.find(({ name }) => name === action.to)) {
                return state;
            }
            return {
                ...state,
                columns: state.columns.map(({ name, ...col }) => ({ name: name === action.from ? action.to : name, ...col })),
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
            };
        case ActionTypes.changeColumnType:
            if (!(action.column >= 0 && action.dataType)) {
                return state;
            }
            return {
                ...state,
                columns: state.columns.map((column, i) => {
                    if (i !== action.column) {
                        return column;
                    }
                    return { ...column, type: action.dataType };
                }),
            };
        case ActionTypes.columnResize:
            return {
                ...state,
                columns: state.columns.map?.((column, i) => {
                    if (i !== action.column) {
                        return column;
                    }
                    return { ...column, size: action.size.toString() };
                }),
            };
        case ActionTypes.resetColumnsSize:
            return {
                ...state,
                columns: state.columns.map(({ size, ...c }, i) => (action.columns.includes(i) ? c : { ...c, size })),
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

    const fromExpression = useMemo(() => getParser(language), [language]);
    const toExpression = useMemo(() => getStringifier(language), [language]);

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
