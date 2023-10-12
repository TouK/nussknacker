import HttpService from "../../../../../http/HttpService";
import { ExpressionLang } from "./types";

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
        private variables: Record<string, any>,
        private processingType: string,
        private httpService: typeof HttpService,
    ) {}

    suggestionsFor = (inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> =>
        this.httpService
            .getExpressionSuggestions(
                this.processingType,
                {
                    language: this.language,
                    expression: inputValue,
                },
                caretPosition2d,
                this.variables,
            )
            .then((response) => response.data);
}
