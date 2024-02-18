import { Dispatch, useEffect, useMemo, useReducer, useState } from "react";
import { ExpressionObj } from "../../types";
import { reducer } from "./reducer";
import { Action, ActionTypes } from "./action";
import { getParser } from "./expressionParser";
import { getStringifier } from "./expressionStringifier";

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

export function useTableState(expressionObj: ExpressionObj): [TableData, Dispatch<Action>, string] {
    const [rawExpression, setRawExpression] = useState<string>(expressionObj.expression);

    const fromExpression = useMemo(() => getParser(expressionObj.language), [expressionObj.language]);
    const toExpression = useMemo(() => getStringifier(expressionObj.language), [expressionObj.language]);

    const [state, dispatch] = useReducer(reducer, emptyValue, (defaultValue) => fromExpression(expressionObj.expression, defaultValue));

    useEffect(() => {
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
        setRawExpression(toExpression(state));
    }, [state, toExpression]);

    return [state, dispatch, rawExpression];
}
