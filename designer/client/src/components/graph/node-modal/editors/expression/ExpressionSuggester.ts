import _ from "lodash"
import {ClassDefinition} from "../../../../../types";
import HttpService from "../../../../../http/HttpService";
import {ExpressionLang} from "./types";

// before indexer['last indexer key
const INDEXER_REGEX = /^(.*)\['([^\[]*)$/

export type CaretPosition2d = {row: number, column: number};
export type ExpressionSuggestion = {
  methodName: string;
  refClazz: {display: string};
  fromClass: boolean;
  description?: string;
  parameters?: any;
}
export interface ExpressionSuggester {
  suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]>;
}

export class BackendExpressionSuggester implements ExpressionSuggester {

  constructor(private processId: string, private _typesInformation: ClassDefinition[], private variables: Record<string, any>, private _processingType: string, private _httpService: typeof HttpService) {}
  suggestionsFor(inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> {
    return this._httpService.getExpressionSuggestions(this.processId, {language: ExpressionLang.SpEL, expression: inputValue}, caretPosition2d, this.variables).then(response => response.data);
  }
}

export class RegexExpressionSuggester implements ExpressionSuggester {
  readonly _variables: Record<string, any>;

  constructor(private _typesInformation: ClassDefinition[], variables, private _processingType: string, private _httpService: Pick<typeof HttpService, "fetchDictLabelSuggestions">) {
    this._variables = _.mapKeys(variables, (value, variableName) => {return `#${variableName}`})
  }

  suggestionsFor = (inputValue: string, caretPosition2d: CaretPosition2d): Promise<ExpressionSuggestion[]> => {
    const normalized = this._normalizeMultilineInputToSingleLine(inputValue, caretPosition2d)
    const lastExpressionPart = this._focusedLastExpressionPartWithoutMethodParens(normalized.normalizedInput, normalized.normalizedCaretPosition)
    const properties = this._alreadyTypedProperties(lastExpressionPart)
    const variablesIncludingSelectionOrProjection = this._getAllVariables(normalized)
    const focusedClazz = this._findRootClazz(properties, variablesIncludingSelectionOrProjection)
    return this._getSuggestions(lastExpressionPart, focusedClazz, variablesIncludingSelectionOrProjection)
  }

  _normalizeMultilineInputToSingleLine = (inputValue: string, caretPosition2d: CaretPosition2d) => {
    const rows = inputValue?.split("\n") || []
    const trimmedRows = _.map(rows, (row) => {
      const trimmedAtStartRow = _.dropWhile(row, (c) => c === " ").join("")
      return {trimmedAtStartRow: trimmedAtStartRow, trimmedCount: row.length - trimmedAtStartRow.length}
    })
    const beforeCaretInputLength = _.sum(_.map(_.take(trimmedRows, caretPosition2d.row), (row) => row.trimmedAtStartRow.length))
    const normalizedCaretPosition = caretPosition2d.column - trimmedRows[caretPosition2d.row].trimmedCount + beforeCaretInputLength
    const normalizedInput = _.map(trimmedRows, (row) => row.trimmedAtStartRow).join("")
    return {
      normalizedInput: normalizedInput,
      normalizedCaretPosition: normalizedCaretPosition,
    }
  }

  _getSuggestions = (value: string, focusedClazz, variables: Record<string, any>): Promise<ExpressionSuggestion[]> => {
    const variableNames = _.keys(variables)
    const variableAlreadySelected = _.some(variableNames, (variable) => { return _.includes(value, `${variable}.`) || _.includes(value, `${variable}['`) })
    const variableNotSelected = _.some(variableNames, (variable) => { return _.startsWith(variable.toLowerCase(), value.toLowerCase()) })
    if (variableAlreadySelected && focusedClazz) {
      const currentType = this._getTypeInfo(focusedClazz)
      const inputValue = this._justTypedProperty(value)
      if (currentType.dict == null) {
        const allowedMethodList = this._getAllowedMethods(currentType)
        const result = inputValue.length === 0 ? allowedMethodList : this._filterSuggestionsForInput(allowedMethodList, inputValue)
        return new Promise(resolve => resolve(result))
      } else {
        return this._getSuggestionsForDict(currentType.dict, inputValue)
      }
    } else if (variableNotSelected && !_.isEmpty(value)) {
      const allVariablesWithClazzRefs = _.map(variables, (val, key) => {
        return {methodName: key, refClazz: val}
      })
      const result = this._filterSuggestionsForInput(allVariablesWithClazzRefs, value)
      return new Promise(resolve => resolve(result))
    } else {
      return new Promise(resolve => resolve([]))
    }
  }

  _getAllowedMethods(currentType) {
    if (currentType.union != null) {
      const allMethods = _.flatMap(currentType.union, (subType) => this._getAllowedMethodsForClass(subType))
      // TODO: compute union of extracted methods types
      return _.uniqWith(allMethods, (typeA, typeB) => _.isEqual(typeA, typeB))
    } else {
      return this._getAllowedMethodsForClass(currentType)
    }
  }

  _getAllowedMethodsForClass(currentType) {
    return _.map(currentType.methods, (val, key) => {
      return {...val, methodName: key}
    })
  }

  _filterSuggestionsForInput = (variables, inputValue: string) => {
    return _.filter(variables, (variable) => {
      return _.includes(variable.methodName.toLowerCase(), inputValue.toLowerCase())
    })
  }

  _findRootClazz = (properties: string[], variables: Record<string, any>) => {
    const variableName = properties[0]
    if (_.has(variables, variableName)) {
      const variableClazzName = _.get(variables, variableName)
      return _.reduce(_.tail(properties), (currentParentClazz, prop) => {
        const parentType = this._getTypeInfo(currentParentClazz)
        return this._extractMethod(parentType, prop)
      }, variableClazzName)
    } else {
      return null
    }
  }

  _extractMethod(type, prop: string) {
    if (type.union != null) {
      let foundedTypes = _.filter(_.map(type.union, (clazz) => this._extractMethodFromClass(clazz, prop)), i => i != null)
      // TODO: compute union of extracted methods types
      return _.first(foundedTypes) || {refClazzName: ""}
    } else {
      return this._extractMethodFromClass(type, prop) || {refClazzName: ""}
    }
  }

  _extractMethodFromClass(clazz, prop: string) {
    return _.get(clazz.methods, `${prop}.refClazz`)
  }

  _getTypeInfo = (type) => {
    if (type.union != null) {
      const unionOfTypeInfos = _.map(type.union, (clazz) => this._getTypeInfoFromClass(clazz))
      return {
        union: unionOfTypeInfos,
      }
    } else {
      return this._getTypeInfoFromClass(type)
    }
  }

  _getTypeInfoFromClass = (clazz) => {
    const methodsFromClass = _.mapValues(this._getMethodsFromGlobalTypeInfo(clazz), (m => ({...m, fromClass: !!clazz.fields})))
    const methodsFromFields = _.mapValues(clazz.fields || [], (field) => ({refClazz: field}))
    const allMethods = _.merge(methodsFromFields, methodsFromClass)

    return {
      ...clazz,
      methods: allMethods,
    }
  }

  _getMethodsFromGlobalTypeInfo = (clazz) => {
    const foundData = _.find(this._typesInformation, {clazzName: {refClazzName: clazz.refClazzName}})
    return !_.isEmpty(foundData) ? foundData.methods : []
  }

  _focusedLastExpressionPartWithoutMethodParens = (expression: string, caretPosition: number) => {
    return this._lastExpressionPartWithoutMethodParens(this._currentlyFocusedExpressionPart(expression, caretPosition))
  }

  _currentlyFocusedExpressionPart = (value: string, caretPosition: number): string => {
    return this._removeFinishedSelectionFromExpressionPart(value.slice(0, caretPosition))
  }

  //TODO: this does not handle map indices properly... e.g. #input.value.?[#this[""] > 4]
  _removeFinishedSelectionFromExpressionPart = (currentExpression: string): string => {
    return currentExpression.replace(/\.\?\[[^\]]*]/g, "")
  }

  _lastExpressionPartWithoutMethodParens = (value: string): string => {
    //we have to handle cases like: #util.now(#other.quaxString.toUpperCase().__)
    const withoutNestedParenthesis = value.substring(this._lastNonClosedParenthesisIndex(value) + 1, value.length)
    const valueCleaned = withoutNestedParenthesis.replace(/\(.*\)/, "")
    //handling ?. operator
    const withSafeNavigationIgnored = valueCleaned.replace(/\?\./g, ".")
    return _.isEmpty(value) ? "" : `#${  _.last(_.split(withSafeNavigationIgnored, "#"))}`
  };

  _lastNonClosedParenthesisIndex = (value: string): number => {
    let nestingCounter = 0
    for (let i = value.length - 1; i >= 0; i--) {
      if (value[i] === "(") nestingCounter -= 1
      else if (value[i] === ")") nestingCounter += 1

      if (nestingCounter < 0) return i
    }
    return -1
  };

  _justTypedProperty = (value: string): string => {
    return _.last(this._dotSeparatedToProperties(value))
  }

  _alreadyTypedProperties = (value: string): string[] => {
    return _.initial(this._dotSeparatedToProperties(value))
  }

  _dotSeparatedToProperties = (value: string): string[] => {
    // TODO: Implement full SpEL support for accessing by indexer the same way as by properties - not just for last indexer
    const indexerMatch = value.match(INDEXER_REGEX)
    if (indexerMatch) {
      return this._dotSeparatedToPropertiesIncludingLastIndexerKey(indexerMatch)
    } else {
      return _.split(value, ".")
    }
  }

  _dotSeparatedToPropertiesIncludingLastIndexerKey = (indexerMatch) => {
    const beforeIndexer = indexerMatch[1]
    const indexerKey = indexerMatch[2]
    const splittedProperties = _.split(beforeIndexer, ".")
    return _.concat(splittedProperties, indexerKey)
  }

  _getAllVariables = (normalized): Record<string, any> => {
    const thisClazz = this._findProjectionOrSelectionRootClazz(normalized)
    const data = thisClazz ? {"#this" : thisClazz} : {}
    return _.merge(data, this._variables)
  }

  _findProjectionOrSelectionRootClazz = (normalized) => {
    const currentProjectionOrSelection = this._findCurrentProjectionOrSelection(normalized)
    if (currentProjectionOrSelection) {
      const properties = this._alreadyTypedProperties(currentProjectionOrSelection)
      //TODO: currently we don't assume nested selections/projections
      const focusedClazz = this._findRootClazz(properties, this._variables)
      return this._getFirstParameterType(focusedClazz)
    } else {
      return null
    }
  }

  _getFirstParameterType = (typ) => {
    if ((typ || {}).union != null) {
      const listOfFirstParams = _.filter(_.map(typ.union, element => {
        return this._getFirstParameterType(element)
      }), i => i != null)
      if (_.isEmpty(listOfFirstParams)) {
        return null
      } else if (listOfFirstParams.length === 1) {
        return listOfFirstParams[0]
      } else {
        // TODO: displayed type
        return {
          display: "Union",
          type: "TypedUnion",
          union: listOfFirstParams
        }
      }
    } else {
      return (typ || {}).params ? typ.params[0] : null
    }
  }

  _findCurrentProjectionOrSelection = (normalized): string => {
    const input = normalized.normalizedInput
    const caretPosition = normalized.normalizedCaretPosition
    const currentPart = this._currentlyFocusedExpressionPart(input, caretPosition)
    //TODO: detect if it's *really* selection/projection (can be in quoted string, or method index??)
    const lastOpening = Math.max(currentPart.lastIndexOf("!["), currentPart.lastIndexOf("?["), currentPart.lastIndexOf("^["), currentPart.lastIndexOf("$["))
    const isInMiddleOfProjectionSelection = lastOpening > currentPart.indexOf("]")
    if (isInMiddleOfProjectionSelection) {
      const currentSelectionProjectionPart = currentPart.slice(0, lastOpening)
      //TODO: this won't handle former projections - but we don't support them now anyway...
      return this._lastExpressionPartWithoutMethodParens(currentSelectionProjectionPart)
    } else {
      return null
    }

  }

  _getSuggestionsForDict = (typ, typedProperty): Promise<ExpressionSuggestion[]> => {
    return this._fetchDictLabelSuggestions(typ.id, typedProperty).then(result => _.map(result.data, entry => {
      return {
        methodName: entry.label,
        refClazz: typ.valueType,
        fromClass: false
      }
    }))
  }

  _fetchDictLabelSuggestions = (dictId, labelPattern) => {
    return this._httpService.fetchDictLabelSuggestions(this._processingType, dictId, labelPattern)
  }

}
