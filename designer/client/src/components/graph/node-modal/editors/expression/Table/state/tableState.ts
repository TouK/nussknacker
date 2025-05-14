import type { Dispatch } from "react";
import { useEffect, useMemo, useReducer, useState } from "react";

import type { ExpressionObj } from "../../types";
import type { SupportedType } from "../TableEditor";
import type { Action } from "./action";
import { ActionTypes } from "./action";
import { getParser } from "./expressionParser";
import { getStringifier } from "./expressionStringifier";
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

export function useTableState(expressionObj: ExpressionObj): [TableData, Dispatch<Action>, string] {
    const [rawExpression, setRawExpression] = useState<string>(expressionObj.expression);

    const fromExpression = useMemo(() => getParser(expressionObj.language), [expressionObj.language]);
    const toExpression = useMemo(() => getStringifier(expressionObj.language), [expressionObj.language]);

    const [state, dispatch] = useReducer(reducer, emptyValue, (defaultValue) => fromExpression(expressionObj.expression, defaultValue));

    useEffect(() => {
        // synchronize rawExpression & state with new expressionObj
        setRawExpression((currentRawExpression: string) => {
            if (currentRawExpression === expressionObj.expression) {
                return currentRawExpression;
            }

            dispatch({
                type: ActionTypes.replaceData,
                data: fromExpression(expressionObj.expression, emptyValue),
            });
            return expressionObj.expression;
        });
    }, [expressionObj.expression, fromExpression]);

    useEffect(() => {
        // synchronize rawExpression with new state
        setRawExpression(toExpression(state));
    }, [state, toExpression]);

    return [state, dispatch, rawExpression];
}
