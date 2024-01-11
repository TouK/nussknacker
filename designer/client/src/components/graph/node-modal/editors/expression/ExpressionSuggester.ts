import HttpService from "../../../../../http/HttpService";
import { ExpressionLang } from "./types";
import { VariableTypes } from "../../../../../types";

export type CaretPosition2d = { row: number; column: number };

export type ExpressionSuggestion = {
    methodName: string;
    refClazz: { display: string };
    fromClass: boolean;
    description?: string;
    parameters?: { name: string; refClazz: { display: string } }[];
};

export interface ExpressionSuggester {
    suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]>;
}

export class BackendExpressionSuggester implements ExpressionSuggester {
    constructor(
        private language: ExpressionLang | string,
        private variableTypes: VariableTypes,
        private processingType: string,
        private httpService: typeof HttpService,
    ) {}

    suggestionsFor = (inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> =>
        this.httpService
            .getExpressionSuggestions(this.processingType, {
                expression: {
                    language: this.language,
                    expression: inputValue,
                },
                caretPosition2d,
                variableTypes: this.variableTypes,
            })
            .then((response) => response.data);
}
