import { TableData } from "./tableState";
import { expandTable, longestRow } from "./helpers";
import { Action, ActionTypes } from "./action";

export function reducer(state: TableData, action: Action): TableData {
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

            return {
                ...updatedData,
                rows,
            };
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
                    return {
                        ...column,
                        type: action.dataType,
                    };
                }),
            };
        case ActionTypes.columnResize:
            return {
                ...state,
                columns: state.columns.map?.((column, i) => {
                    if (i !== action.column) {
                        return column;
                    }
                    return {
                        ...column,
                        size: action.size.toString(),
                    };
                }),
            };
        case ActionTypes.resetColumnsSize:
            return {
                ...state,
                columns: state.columns.map(({ size, ...c }, i) =>
                    action.columns.includes(i)
                        ? c
                        : {
                              ...c,
                              size,
                          },
                ),
            };
        case ActionTypes.expand:
            return expandTable(state, action.rows, action.columns, action.dataType);
        case ActionTypes.replaceData:
            return action.data;
        default:
            return state;
    }
}
