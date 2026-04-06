import { isEqual } from "lodash";
import type { Dispatch } from "react";
import { useEffect, useMemo, useReducer, useState } from "react";

import { useFirstRender } from "../../../../../../../containers/hooks/useFirstRender";
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
    const isFirstRender = useFirstRender();

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
        const expressionAndStateAreEqual = isEqual(state, fromExpression(expressionObj.expression));

        // There are two rerenders on init, with empty state and with the parsed state. Let's skip the first one,
        // Also when we add empty table editor, backend sends empty rows and columns, and we set initial table editor state in the ActionTypes.expand
        const firstRender = isFirstRender();
        if (expressionAndStateAreEqual || firstRender) {
            return;
        }

        // synchronize rawExpression with new state
        setRawExpression(toExpression(state));
    }, [expressionObj.expression, state, fromExpression, toExpression, isFirstRender]);

    return [state, dispatch, rawExpression];
}
