import { ClassDefinition, TypingResult } from "../../../../../types";
import HttpService from "../../../../../http/HttpService";
import { ExpressionLang } from "./types";
import {
    concat,
    dropWhile,
    filter,
    find,
    first,
    flatMap,
    get,
    has,
    includes,
    initial,
    isEmpty,
    isEqual,
    keys,
    last,
    map,
    mapKeys,
    mapValues,
    merge,
    reduce,
    some,
    split,
    startsWith,
    sum,
    tail,
    take,
    uniqWith,
} from "lodash";

// before indexer['last indexer key
const INDEXER_REGEX = /^(.*)\['([^[]*)$/;

export type CaretPosition2d = { row: number; column: number };
export type ExpressionSuggestion = {
    methodName: string;
    refClazz: { display: string };
    fromClass: boolean;
    description?: string;
    parameters?: { name: string; refClazz: { display: string } }[];
};

type NormalizedInput = {
    normalizedInput: string;
    normalizedCaretPosition: number;
};

export interface ExpressionSuggester {
    suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]>;
}

export class BackendExpressionSuggester implements ExpressionSuggester {
    constructor(
        private language: ExpressionLang | string,
        private processId: string,
        private variables: Record<string, any>,
        private _processingType: string,
        private _httpService: typeof HttpService,
    ) {}
    suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> {
        return this._httpService
            .getExpressionSuggestions(this.processId, { language: this.language, expression: inputValue }, caretPosition2d, this.variables)
            .then((response) => response.data);
    }
}
