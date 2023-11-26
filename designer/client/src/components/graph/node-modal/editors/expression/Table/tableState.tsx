import { Dispatch, useEffect, useReducer, useState } from "react";
import { getNextColumnName, longestRow, parseSpel, toSpel } from "./tableDataUtils";

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

type Action =
    | {
          type: "replace-data";
          data: TableData;
      }
    | {
          type: "expand";
          rows: number;
          columns: number;
          dataType?: string;
      }
    | {
          type: "edit-data";
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
      }
    | {
          type: "insert-data";
          column: number;
          row: number;
          input: readonly (readonly string[])[];
          dataType?: string;
      }
    | {
          type: "delete-rows";
          rows: number[];
          columnData?: number[];
      }
    | {
          type: "delete-columns";
          columns: number[];
      }
    | {
          type: "reset-columns-size";
          columns: number[];
      }
    | {
          type: "column-resize";
          column: number;
          size: number;
      }
    | {
          type: "rename-column";
          from: string;
          to: string;
      }
    | {
          type: "change-column-type";
          column: number;
          dataType: string;
      };

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
        case "insert-data": {
            const longestColumnLength = longestRow(state.columns).length;
            const extraRowsCount = longestColumnLength - 3;
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
        case "edit-data":
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
        case "rename-column":
            // prevent duplicates
            if (state.columns.find(([name]) => name === action.to)) {
                return state;
            }
            return {
                ...state,
                columns: state.columns.map(([name, ...col]) => [name === action.from ? action.to : name, ...col]),
            };
        case "delete-columns":
            return {
                ...state,
                columns: state.columns.filter((_, i) => !action.columns.includes(i)),
                rows: state.rows.map((r) => r.filter((_, i) => !action.columns.includes(i))),
            };
        case "delete-rows":
            return {
                ...state,
                rows: state.rows.filter((_, i) => !action.rows.includes(i)),
                columns: state.columns.map((r) => r.filter((_, i) => !action.columnData?.includes(i))),
            };
        case "change-column-type":
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
        case "column-resize":
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
        case "reset-columns-size":
            return {
                ...state,
                columns: state.columns.map((c, i) => (!action.columns.includes(i) ? c : c.slice(0, 2))),
            };
        case "expand":
            return expandTable(state, action.rows, action.columns, action.dataType);
        case "replace-data":
            return action.data;
        default:
            return state;
    }
}

export function useTableState(expression: string): [TableData, Dispatch<Action>, string] {
    const [rawExpression, setRawExpression] = useState<string>(expression);

    const [state, dispatch] = useReducer(reducer, emptyValue, (defaultValue) => parseSpel(expression, defaultValue));

    useEffect(() => {
        setRawExpression((current) => {
            if (current === expression) {
                return current;
            }
            dispatch({
                type: "replace-data",
                payload: parseSpel(expression, emptyValue),
            });
            return expression;
        });
    }, [expression]);

    useEffect(() => {
        setRawExpression(toSpel(state));
    }, [state, setRawExpression]);

    return [state, dispatch, rawExpression];
}
