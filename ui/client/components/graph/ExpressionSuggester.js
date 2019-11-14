import _ from 'lodash';

export default class ExpressionSuggester {

  constructor(typesInformation, variables) {
    this._typesInformation = typesInformation
    this._variables = _.mapKeys(variables, (value, variableName) => {return `#${variableName}`})
  }

  suggestionsFor = (inputValue, caretPosition2d) => {
    const normalized = this._normalizeMultilineInputToSingleLine(inputValue, caretPosition2d)
    const lastExpressionPart = this._focusedLastExpressionPartWithoutMethodParens(normalized.normalizedInput, normalized.normalizedCaretPosition)
    const properties = this._alreadyTypedProperties(lastExpressionPart)
    const variablesIncludingSelectionOrProjection = this._getAllVariables(normalized);
    const focusedClazz = this._findRootClazz(properties, variablesIncludingSelectionOrProjection)
    return this._getSuggestions(lastExpressionPart, focusedClazz, variablesIncludingSelectionOrProjection)
  }

  _normalizeMultilineInputToSingleLine = (inputValue, caretPosition2d) => {
    const rows = inputValue.split("\n")
    const trimmedRows = _.map(rows, (row) => {
      const trimmedAtStartRow = _.dropWhile(row, (c) => c === " ").join("")
      return { trimmedAtStartRow: trimmedAtStartRow, trimmedCount: row.length - trimmedAtStartRow.length }
    })
    const beforeCaretInputLength = _.sum(_.map(_.take(trimmedRows, caretPosition2d.row), (row) => row.trimmedAtStartRow.length));
    const normalizedCaretPosition = caretPosition2d.column - trimmedRows[caretPosition2d.row].trimmedCount + beforeCaretInputLength
    const normalizedInput = _.map(trimmedRows, (row) => row.trimmedAtStartRow).join("")
    return {
      normalizedInput: normalizedInput,
      normalizedCaretPosition: normalizedCaretPosition
    }
  }

  _getSuggestions = (value, focusedClazz, variables) => {
    const variableNames = _.keys(variables)
    const variableAlreadySelected = _.some(variableNames, (variable) => { return _.includes(value, `${variable}.`) })
    const variableNotSelected = _.some(variableNames, (variable) => { return _.startsWith(variable.toLowerCase(), value.toLowerCase()) })
    let result;
    if (variableAlreadySelected && focusedClazz) {
      const currentType = this._getTypeInfo(focusedClazz)
      const inputValue = this._justTypedProperty(value)
      const allowedMethodList = this._getAllowedMethods(currentType)
      result = inputValue.length === 0 ? allowedMethodList : this._filterSuggestionsForInput(allowedMethodList, inputValue)
    } else if (variableNotSelected && !_.isEmpty(value)) {
      const allVariablesWithClazzRefs = _.map(variables, (val, key) => {
        return {'methodName': key, 'refClazz': val}
      })
      result = this._filterSuggestionsForInput(allVariablesWithClazzRefs, value)
    } else {
      result = []
    }
    return new Promise((resolve, reject) => resolve(result))
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

  _filterSuggestionsForInput = (variables, inputValue) => {
    return _.filter(variables, (variable) => {
      return _.includes(variable.methodName.toLowerCase(), inputValue.toLowerCase())
    })
  }

  _findRootClazz = (properties, variables) => {
    const variableName = properties[0]
    if (_.has(variables, variableName)) {
      const variableClazzName = _.get(variables, variableName);
      return _.reduce(_.tail(properties), (currentParentClazz, prop) => {
        const parentType = this._getTypeInfo(currentParentClazz)
        return this._extractMethod(parentType, prop)
      }, variableClazzName)
    } else {
      return null
    }
  }

  _extractMethod(type, prop) {
    if (type.union != null) {
      let foundedTypes = _.filter(_.map(type.union, (clazz) => this._extractMethodFromClass(clazz, prop)), i => i != null)
      // TODO: compute union of extracted methods types
      return _.first(foundedTypes) || {refClazzName: ''}
    } else {
      return this._extractMethodFromClass(type, prop) || {refClazzName: ''}
    }
  }

  _extractMethodFromClass(clazz, prop) {
    return _.get(clazz.methods, `${prop}.refClazz`)
  }

  _getTypeInfo = (type) => {
    if (type.union != null) {
      const unionOfTypeInfos = _.map(type.union, (clazz) => this._getTypeInfoFromClass(clazz))
      return {
        union: unionOfTypeInfos
      }
    } else {
      return this._getTypeInfoFromClass(type)
    }
  }

  _getTypeInfoFromClass = (clazz) => {
    const methodsFromInfo = this._getMethodsFromGlobalTypeInfo(clazz);
    const methodsFromFields = _.mapValues((clazz.fields || []), (field) => ({ refClazz: field }));
    const allMethods = _.merge(methodsFromFields, methodsFromInfo);

    return {
      ...clazz,
      methods: allMethods
    }
  }

  _getMethodsFromGlobalTypeInfo = (clazz) => {
    const foundData = _.find(this._typesInformation, { clazzName: { refClazzName: clazz.refClazzName }})
    return !_.isEmpty(foundData) ? foundData.methods : []
  }

  _focusedLastExpressionPartWithoutMethodParens = (expression, caretPosition) => {
    return this._lastExpressionPartWithoutMethodParens(this._currentlyFocusedExpressionPart(expression, caretPosition))
  }

  _currentlyFocusedExpressionPart = (value, caretPosition) => {
    return this._removeFinishedSelectionFromExpressionPart(value.slice(0, caretPosition))
  }

  //TODO: this does not handle map indices properly... e.g. #input.value.?[#this[""] > 4]
  _removeFinishedSelectionFromExpressionPart = (currentExpression) => {
    return currentExpression.replace(/\.\?\[[^\]]*]/g, "")
  }

  _lastExpressionPartWithoutMethodParens = (value) => {
    const withoutNestedParenthesis = value.substring(this._lastNonClosedParenthesisIndex(value) + 1, value.length);
    const valueCleaned = withoutNestedParenthesis.replace(/\(.*\)/, "");
    return _.isEmpty(value) ? "" : "#" + _.last(_.split(valueCleaned, '#'))
  };

  _lastNonClosedParenthesisIndex = (value) => {
    let nestingCounter = 0;
    for (let i = value.length - 1; i >= 0; i--) {
      if (value[i] === "(") nestingCounter -= 1;
      else if (value[i] === ")") nestingCounter += 1;

      if (nestingCounter < 0) return i;
    }
    return -1;
  };

  _justTypedProperty = (value) => {
    return _.last(this._dotSeparatedToProperties(value))
  }

  _alreadyTypedProperties = (value) => {
    return _.initial(this._dotSeparatedToProperties(value))
  }

  _dotSeparatedToProperties = (value) => {
    return _.split(value, ".")
  }

  _getAllVariables = (normalized) => {
    const thisClazz = this._findProjectionOrSelectionRootClazz(normalized)
    const data = thisClazz ? { '#this' : thisClazz } : {};
    return _.merge(data, this._variables)
  }

  _findProjectionOrSelectionRootClazz = (normalized) => {
    const currentProjectionOrSelection = this._findCurrentProjectionOrSelection(normalized)
    if (currentProjectionOrSelection) {
      const properties = this._alreadyTypedProperties(currentProjectionOrSelection)
      //TODO: currently we don't assume nested selections/projections
      const focusedClazz = this._findRootClazz(properties, this._variables)
      return (focusedClazz || {}).params ? focusedClazz.params[0] : null
    } else {
      return null
    }
  }

  _findCurrentProjectionOrSelection = (normalized) => {
    const input = normalized.normalizedInput;
    const caretPosition = normalized.normalizedCaretPosition;
    const currentPart = this._currentlyFocusedExpressionPart(input, caretPosition)
    //TODO: detect if it's *really* selection/projection (can be in quoted string, or method index??)
    const lastOpening = Math.max(currentPart.lastIndexOf("!["), currentPart.lastIndexOf("?["))
    const isInMiddleOfProjectionSelection = lastOpening > currentPart.indexOf("]")
    if (isInMiddleOfProjectionSelection) {
      const currentSelectionProjectionPart = currentPart.slice(0, lastOpening)
      //TODO: this won't handle former projections - but we don't support them now anyway...
      return this._lastExpressionPartWithoutMethodParens(currentSelectionProjectionPart)
    } else {
      return null;
    }

  }

}