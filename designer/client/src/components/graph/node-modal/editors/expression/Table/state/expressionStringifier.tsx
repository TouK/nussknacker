import { ExpressionLang } from "../../types";
import { TableData } from "./tableState";

export const getStringifier: (language: ExpressionLang | string) => (data: TableData) => string = (language: ExpressionLang | string) => {
    switch (language) {
        case ExpressionLang.JSON:
        case ExpressionLang.TabularDataDefinition:
            return (data) => JSON.stringify(data, null, 2);
    }
    throw `No stringifier for ${language} found!`;
};
