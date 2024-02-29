import { ExpressionLang } from "../../types";
import { TableData } from "./tableState";

const parseWithDefault =
    (parse: (text: string) => TableData) =>
    (expression: string, emptyValue?: TableData): TableData => {
        try {
            const { rows, columns } = parse(expression);
            return {
                rows,
                columns,
            };
        } catch (error) {
            console.warn(error);
            return emptyValue;
        }
    };

export const getParser = (language: ExpressionLang | string): ((expression: string, emptyValue?: TableData) => TableData) => {
    switch (language) {
        case ExpressionLang.JSON:
        case ExpressionLang.TabularDataDefinition:
            return parseWithDefault(JSON.parse);
    }
    throw `No parser for ${language} found!`;
};
