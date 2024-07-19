import HttpService from "../../../../../http/HttpService";
import { ExpressionLang } from "./types";
import { VariableTypes } from "../../../../../types";

type Arguments<T> = T extends (...args: infer A) => any ? A : never;

export type CaretPosition2d = {
    row: number;
    column: number;
};

export type ExpressionSuggestion = {
    methodName: string;
    refClazz: {
        display: string;
        type: string;
    };
    fromClass: boolean;
    description?: string;
    parameters?: {
        name: string;
        refClazz: {
            display: string;
        };
    }[];
};

export interface ExpressionSuggester {
    suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]>;

    cancel(): void;
}

export enum SuggesterEvent {
    stateChange = "stateChange",
}

type EventsMap = {
    [SuggesterEvent.stateChange]: (state: boolean) => void;
};

interface MapOfSets<Key extends string, Value extends Record<Key, any>> extends Map<Key, Set<Value[Key]>> {
    forEach(callbackfn: <K extends Key>(value: Set<Value[K]>, key: K, map: MapOfSets<Key, Value>) => void, thisArg?: any): void;

    has(key: Key | `${Key}`): boolean;

    get<K extends Key>(key: K | `${K}`): Set<Value[K]> | undefined;

    set<K extends Key>(key: K | `${K}`, value: Set<Value[K]>): this;
}

export class BackendExpressionSuggester implements ExpressionSuggester {
    private readonly handlers: MapOfSets<SuggesterEvent, EventsMap> = new Map();
    private latestRequest: Promise<ExpressionSuggestion[]> = Promise.resolve([]);

    constructor(
        private language: ExpressionLang | string,
        private variableTypes: VariableTypes,
        private processingType: string,
        private httpService: typeof HttpService,
    ) {}

    private _isLoading = false;

    get isLoading() {
        return this._isLoading;
    }

    set isLoading(value: boolean) {
        if (this.isLoading === value) return;

        this._isLoading = value;
        this.callHandlers(SuggesterEvent.stateChange, [this.isLoading]);
    }

    on<E extends SuggesterEvent>(eventName: E | `${E}`, callback: EventsMap[E]): () => void {
        this.getHandlers(eventName).add(callback);

        return () => this.off(eventName, callback);
    }

    off<E extends SuggesterEvent>(eventName: E | `${E}`, callback: EventsMap[E]): void {
        this.getHandlers(eventName).delete(callback);
    }

    suggestionsFor(expression: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> {
        this.isLoading = true;
        const currentRequest = this.httpService
            .getExpressionSuggestions(this.processingType, {
                expression: {
                    language: this.language,
                    expression,
                },
                caretPosition2d,
                variableTypes: this.variableTypes,
            })
            .then(
                (response) => response.data,
                () => [],
            );
        this.latestRequest = currentRequest;

        return currentRequest
            .then(() => this.latestRequest)
            .then((suggestions) => {
                this.isLoading = false;
                return suggestions;
            });
    }

    cancel(): void {
        this.isLoading = false;
        this.latestRequest = Promise.resolve([]);
    }

    private callHandlers<E extends SuggesterEvent>(eventName: `${E}` | E, args: Arguments<EventsMap[E]>) {
        this.getHandlers(eventName).forEach((fn) => fn.apply(this, args));
    }

    private getHandlers<E extends SuggesterEvent>(eventName: `${E}` | E): Set<EventsMap[E]> {
        if (!this.handlers.has(eventName)) {
            this.handlers.set<E>(eventName, new Set());
        }

        return this.handlers.get<E>(eventName);
    }
}
