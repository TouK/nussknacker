import type { Dispatch } from "react";
import { useCallback, useEffect, useMemo, useReducer, useRef } from "react";

import type { ExpressionObj } from "../../types";
import type { SupportedType } from "../TableEditor";
import type { Action } from "./action";
import { ActionTypes } from "./action";
import { getParser } from "./expressionParser";
import { getStringifier } from "./expressionStringifier";
import { expandTable } from "./helpers";
import { reducer } from "./reducer";

export type DataColumn = {
    name: string;
    type: SupportedType;
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

function ensureMinimumSize(state: TableData, dataType?: SupportedType): TableData {
    const missingRows = state.rows.length < 1 ? 1 : 0;
    const missingColumns = state.columns.length < 1 ? 1 : 0;
    if (missingRows <= 0 && missingColumns <= 0) return state;
    return expandTable(state, missingRows, missingColumns, dataType);
}

export function useTableState(
    expressionObj: ExpressionObj,
    onChange?: (expression: string) => void,
    defaultDataType?: SupportedType,
): [TableData, Dispatch<Action>] {
    const fromExpression = useMemo(() => getParser(expressionObj.language), [expressionObj.language]);
    const toExpression = useMemo(() => getStringifier(expressionObj.language), [expressionObj.language]);

    const [state, dispatch] = useReducer(reducer, emptyValue, (defaultValue) =>
        ensureMinimumSize(fromExpression(expressionObj.expression, defaultValue), defaultDataType),
    );

    // Keep a ref to the current state so we can compute new state synchronously on dispatch
    const stateRef = useRef(state);
    stateRef.current = state;

    const onChangeRef = useRef(onChange);
    onChangeRef.current = onChange;

    const defaultDataTypeRef = useRef(defaultDataType);
    defaultDataTypeRef.current = defaultDataType;

    // Synchronize state with external expressionObj changes (e.g. undo/redo)
    const lastExternalExpression = useRef(expressionObj.expression);
    useEffect(() => {
        if (lastExternalExpression.current !== expressionObj.expression) {
            lastExternalExpression.current = expressionObj.expression;
            dispatch({
                type: ActionTypes.replaceData,
                data: ensureMinimumSize(fromExpression(expressionObj.expression, emptyValue), defaultDataTypeRef.current),
            });
        }
    }, [expressionObj.expression, fromExpression]);

    const dispatchWithNotify = useCallback(
        (action: Action) => {
            const afterAction = reducer(stateRef.current, action);
            const newState = ensureMinimumSize(afterAction, defaultDataTypeRef.current);

            stateRef.current = newState;
            dispatch(action);
            if (newState !== afterAction) {
                dispatch({
                    type: ActionTypes.expand,
                    rows: afterAction.rows.length < 1 ? 1 : 0,
                    columns: afterAction.columns.length < 1 ? 1 : 0,
                    dataType: defaultDataTypeRef.current,
                });
            }
            if (onChangeRef.current) {
                const newExpression = toExpression(newState);
                onChangeRef.current(newExpression);
            }
        },
        [toExpression],
    );

    return [state, dispatchWithNotify];
}
