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
        private processId: string,
        private _typesInformation: ClassDefinition[],
        private variables: Record<string, any>,
        private _processingType: string,
        private _httpService: typeof HttpService,
    ) {}
    suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> {
        return this._httpService
            .getExpressionSuggestions(
                this.processId,
                { language: ExpressionLang.SpEL, expression: inputValue },
                caretPosition2d,
                this.variables,
            )
            .then((response) => response.data);
    }
}

export class RegexExpressionSuggester implements ExpressionSuggester {
    readonly _variables: Record<string, any>;

    constructor(
        private _typesInformation: ClassDefinition[],
        variables,
        private _processingType: string,
        private _httpService: Pick<typeof HttpService, "fetchDictLabelSuggestions">,
    ) {
        this._variables = mapKeys(variables, (value, variableName) => {
            return `#${variableName}`;
        });
    }

    suggestionsFor = (inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> => {
        const normalized = this._normalizeMultilineInputToSingleLine(inputValue, caretPosition2d);
        const lastExpressionPart = this._focusedLastExpressionPartWithoutMethodParens(
            normalized.normalizedInput,
            normalized.normalizedCaretPosition,
        );
        const properties = this._alreadyTypedProperties(lastExpressionPart);
        const variablesIncludingSelectionOrProjection = this._getAllVariables(normalized);
        const focusedClazz = this._findRootClazz(properties, variablesIncludingSelectionOrProjection);
        return this._getSuggestions(lastExpressionPart, focusedClazz, variablesIncludingSelectionOrProjection);
    };

    _normalizeMultilineInputToSingleLine = (inputValue: string, caretPosition2d: CaretPosition2d): NormalizedInput => {
        const rows = inputValue?.split("\n") || [];
        const trimmedRows = map(rows, (row) => {
            const trimmedAtStartRow = dropWhile(row, (c) => c === " ").join("");
            return { trimmedAtStartRow: trimmedAtStartRow, trimmedCount: row.length - trimmedAtStartRow.length };
        });
        const beforeCaretInputLength = sum(map(take(trimmedRows, caretPosition2d.row), (row) => row.trimmedAtStartRow.length));
        const normalizedCaretPosition = caretPosition2d.column - trimmedRows[caretPosition2d.row].trimmedCount + beforeCaretInputLength;
        const normalizedInput = map(trimmedRows, (row) => row.trimmedAtStartRow).join("");
        return {
            normalizedInput: normalizedInput,
            normalizedCaretPosition: normalizedCaretPosition,
        };
    };

    _getSuggestions = (value: string, focusedClazz, variables: Record<string, any>): Promise<ExpressionSuggestion[]> => {
        const variableNames = keys(variables);
        const variableAlreadySelected = some(variableNames, (variable) => {
            return includes(value, `${variable}.`) || includes(value, `${variable}['`);
        });
        const variableNotSelected = some(variableNames, (variable) => {
            return startsWith(variable.toLowerCase(), value.toLowerCase());
        });
        if (variableAlreadySelected && focusedClazz) {
            const currentType = this._getTypeInfo(focusedClazz);
            const inputValue = this._justTypedProperty(value);
            if (currentType.dict == null) {
                const allowedMethodList = this._getAllowedMethods(currentType);
                const result = inputValue.length === 0 ? allowedMethodList : this._filterSuggestionsForInput(allowedMethodList, inputValue);
                return new Promise((resolve) => resolve(result));
            } else {
                return this._getSuggestionsForDict(currentType.dict, inputValue);
            }
        } else if (variableNotSelected && !isEmpty(value)) {
            const allVariablesWithClazzRefs = map(variables, (val, key) => {
                return { methodName: key, refClazz: val };
            });
            const result = this._filterSuggestionsForInput(allVariablesWithClazzRefs, value);
            return new Promise((resolve) => resolve(result));
        } else {
            return new Promise((resolve) => resolve([]));
        }
    };

    _getAllowedMethods(currentType) {
        if (currentType.union != null) {
            const allMethods = flatMap(currentType.union, (subType) => this._getAllowedMethodsForClass(subType));
            // TODO: compute union of extracted methods types
            return uniqWith(allMethods, (typeA, typeB) => isEqual(typeA, typeB));
        } else {
            return this._getAllowedMethodsForClass(currentType);
        }
    }

    _getAllowedMethodsForClass(currentType) {
        return map(currentType.methods, (val, key) => {
            return { ...val, methodName: key };
        });
    }

    _filterSuggestionsForInput = (variables, inputValue: string) => {
        return filter(variables, (variable) => {
            return includes(variable.methodName.toLowerCase(), inputValue.toLowerCase());
        });
    };

    _findRootClazz = (properties: string[], variables: Record<string, any>) => {
        const variableName = properties[0];
        if (has(variables, variableName)) {
            const variableClazzName = get(variables, variableName);
            return reduce(
                tail(properties),
                (currentParentClazz, prop) => {
                    const parentType = this._getTypeInfo(currentParentClazz);
                    return this._extractMethod(parentType, prop);
                },
                variableClazzName,
            );
        } else {
            return null;
        }
    };

    _extractMethod(type: TypingResult, prop: string) {
        if ("union" in type && type.union != null) {
            const foundedTypes = filter(
                map(type.union, (clazz) => this._extractMethodFromClass(clazz, prop)),
                (i) => i != null,
            );
            // TODO: compute union of extracted methods types
            return first(foundedTypes) || { refClazzName: "" };
        } else {
            return this._extractMethodFromClass(type, prop) || { refClazzName: "" };
        }
    }

    _extractMethodFromClass(clazz, prop: string) {
        return get(clazz.methods, `${prop}.refClazz`);
    }

    _getTypeInfo = (type: TypingResult) => {
        if ("union" in type && type.union != null) {
            const unionOfTypeInfos = map(type.union, (clazz) => this._getTypeInfoFromClass(clazz));
            return {
                union: unionOfTypeInfos,
            };
        } else {
            return this._getTypeInfoFromClass(type);
        }
    };

    _getTypeInfoFromClass = (clazz) => {
        const methodsFromClass = mapValues(this._getMethodsFromGlobalTypeInfo(clazz), (m) => ({
            ...m,
            fromClass: !!clazz.fields,
        }));
        const methodsFromFields = mapValues(clazz.fields || [], (field) => ({ refClazz: field }));
        const allMethods = merge(methodsFromFields, methodsFromClass);

        return {
            ...clazz,
            methods: allMethods,
        };
    };

    _getMethodsFromGlobalTypeInfo = (clazz: TypingResult) => {
        const foundData = find(this._typesInformation, { clazzName: { refClazzName: clazz.refClazzName } });
        return !isEmpty(foundData) ? foundData.methods : [];
    };

    _focusedLastExpressionPartWithoutMethodParens = (expression: string, caretPosition: number) => {
        return this._lastExpressionPartWithoutMethodParens(this._currentlyFocusedExpressionPart(expression, caretPosition));
    };

    _currentlyFocusedExpressionPart = (value: string, caretPosition: number): string => {
        return this._removeFinishedSelectionFromExpressionPart(value.slice(0, caretPosition));
    };

    //TODO: this does not handle map indices properly... e.g. #input.value.?[#this[""] > 4]
    _removeFinishedSelectionFromExpressionPart = (currentExpression: string): string => {
        return currentExpression.replace(/\.\?\[[^\]]*]/g, "");
    };

    _lastExpressionPartWithoutMethodParens = (value: string): string => {
        //we have to handle cases like: #util.now(#other.quaxString.toUpperCase().__)
        const withoutNestedParenthesis = value.substring(this._lastNonClosedParenthesisIndex(value) + 1, value.length);
        const valueCleaned = withoutNestedParenthesis.replace(/\(.*\)/, "");
        //handling ?. operator
        const withSafeNavigationIgnored = valueCleaned.replace(/\?\./g, ".");
        return isEmpty(value) ? "" : `#${last(split(withSafeNavigationIgnored, "#"))}`;
    };

    _lastNonClosedParenthesisIndex = (value: string): number => {
        let nestingCounter = 0;
        for (let i = value.length - 1; i >= 0; i--) {
            if (value[i] === "(") nestingCounter -= 1;
            else if (value[i] === ")") nestingCounter += 1;

            if (nestingCounter < 0) return i;
        }
        return -1;
    };

    _justTypedProperty = (value: string): string => {
        return last(this._dotSeparatedToProperties(value));
    };

    _alreadyTypedProperties = (value: string): string[] => {
        return initial(this._dotSeparatedToProperties(value));
    };

    _dotSeparatedToProperties = (value: string): string[] => {
        // TODO: Implement full SpEL support for accessing by indexer the same way as by properties - not just for last indexer
        const indexerMatch: RegExpMatchArray = value.match(INDEXER_REGEX);
        if (indexerMatch) {
            return this._dotSeparatedToPropertiesIncludingLastIndexerKey(indexerMatch);
        } else {
            return split(value, ".");
        }
    };

    _dotSeparatedToPropertiesIncludingLastIndexerKey = (indexerMatch: RegExpMatchArray): string[] => {
        const beforeIndexer = indexerMatch[1];
        const indexerKey = indexerMatch[2];
        const splittedProperties = split(beforeIndexer, ".");
        return concat(splittedProperties, indexerKey);
    };

    _getAllVariables = (normalized: NormalizedInput): Record<string, any> => {
        const thisClazz = this._findProjectionOrSelectionRootClazz(normalized);
        const data = thisClazz ? { "#this": thisClazz } : {};
        return merge(data, this._variables);
    };

    _findProjectionOrSelectionRootClazz = (normalized: NormalizedInput) => {
        const currentProjectionOrSelection = this._findCurrentProjectionOrSelection(normalized);
        if (currentProjectionOrSelection) {
            const properties = this._alreadyTypedProperties(currentProjectionOrSelection);
            //TODO: currently we don't assume nested selections/projections
            const focusedClazz = this._findRootClazz(properties, this._variables);
            return this._getFirstParameterType(focusedClazz);
        } else {
            return null;
        }
    };

    _getFirstParameterType = (typ) => {
        if ((typ || {}).union != null) {
            const listOfFirstParams = filter(
                map(typ.union, (element) => {
                    return this._getFirstParameterType(element);
                }),
                (i) => i != null,
            );
            if (isEmpty(listOfFirstParams)) {
                return null;
            } else if (listOfFirstParams.length === 1) {
                return listOfFirstParams[0];
            } else {
                // TODO: displayed type
                return {
                    display: "Union",
                    type: "TypedUnion",
                    union: listOfFirstParams,
                };
            }
        } else {
            return (typ || {}).params ? typ.params[0] : null;
        }
    };

    _findCurrentProjectionOrSelection = (normalized: NormalizedInput): string => {
        const input = normalized.normalizedInput;
        const caretPosition = normalized.normalizedCaretPosition;
        const currentPart = this._currentlyFocusedExpressionPart(input, caretPosition);
        //TODO: detect if it's *really* selection/projection (can be in quoted string, or method index??)
        const lastOpening = Math.max(
            currentPart.lastIndexOf("!["),
            currentPart.lastIndexOf("?["),
            currentPart.lastIndexOf("^["),
            currentPart.lastIndexOf("$["),
        );
        const isInMiddleOfProjectionSelection = lastOpening > currentPart.indexOf("]");
        if (isInMiddleOfProjectionSelection) {
            const currentSelectionProjectionPart = currentPart.slice(0, lastOpening);
            //TODO: this won't handle former projections - but we don't support them now anyway...
            return this._lastExpressionPartWithoutMethodParens(currentSelectionProjectionPart);
        } else {
            return null;
        }
    };

    _getSuggestionsForDict = (typ, typedProperty): Promise<ExpressionSuggestion[]> => {
        return this._fetchDictLabelSuggestions(typ.id, typedProperty).then((result) =>
            map(result.data, (entry) => {
                return {
                    methodName: entry.label,
                    refClazz: typ.valueType,
                    fromClass: false,
                };
            }),
        );
    };

    _fetchDictLabelSuggestions = (dictId, labelPattern) => {
        return this._httpService.fetchDictLabelSuggestions(this._processingType, dictId, labelPattern);
    };
}
