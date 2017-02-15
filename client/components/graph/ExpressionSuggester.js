import _ from 'lodash';

export default class ExpressionSuggester {

  constructor(typesInformation, variables) {
    this._typesInformation = typesInformation
    this._variables = variables
  }

  applySuggestion = (suggestion, inputValue, caretPosition) => {
    const beforeCaret = inputValue.slice(0, caretPosition)
    const afterCaret = inputValue.slice(caretPosition)
    const lastExpressionPart = this._focusedLastExpressionPart(inputValue, caretPosition)
    const firstExpressionPart = _.trimEnd(beforeCaret, lastExpressionPart)
    const suggestionForLastExpression = _.concat(this._alreadyTypedProperties(lastExpressionPart), suggestion.methodName)
    const suggestionValueText = this._propertiesToDotSeparated(suggestionForLastExpression)
    const newBeforeCaret = firstExpressionPart + suggestionValueText
    return {
      value: newBeforeCaret + afterCaret,
      caretPosition: newBeforeCaret.length
    }
  }

  extractMatchingPartFromInput = (suggestion, inputValue, caretPosition) => {
    const justTyped = this._justTypedProperty(this._focusedLastExpressionPartWithoutMethodParens(inputValue, caretPosition))
    const expr = new RegExp(`(.*?)${justTyped}(.*)`, "i")
    const suggestedStartAndEnd = suggestion.methodName.match(expr)
    const start = _.nth(suggestedStartAndEnd, 1)
    const end = _.nth(suggestedStartAndEnd, 2)
    const matchStartIdx = _.get(start, 'length')
    const matchEndIdx = suggestion.methodName.length - _.get(end, 'length')
    const middle = suggestion.methodName.slice(matchStartIdx, matchEndIdx)
    return {start, middle, end}
  }

  suggestionsFor = (inputValue, caretPosition) => {
    const lastExpressionPart = this._focusedLastExpressionPartWithoutMethodParens(inputValue, caretPosition)
    const properties = this._alreadyTypedProperties(lastExpressionPart)
    const focusedClazz = this._findRootClazz(properties)
    return this._getSuggestions(lastExpressionPart, focusedClazz)
  }

  _getSuggestions = (value, focusedClazz) => {
    const variableNames = _.keys(this._variables)
    const variableAlreadySelected = _.some(variableNames, (variable) => { return _.includes(value, `${variable}.`) })
    const variableNotSelected = _.some(variableNames, (variable) => { return _.startsWith(variable.toLowerCase(), value.toLowerCase()) })
    if (variableAlreadySelected && focusedClazz) {
      const currentType = this._getTypeInfo(focusedClazz)
      const inputValue = this._justTypedProperty(value)
      const allowedMethodList = _.map(currentType.methods, (val, key) => { return { ...val, methodName: key} })
      return inputValue.length === 0 ? allowedMethodList : this._filterSuggestionsForInput(allowedMethodList, inputValue)
    } else if (variableNotSelected && !_.isEmpty(value)) {
      const allVariables = _.map(variableNames, (variableName) => { return { methodName: variableName}})
      return this._filterSuggestionsForInput(allVariables, value)
    }
    else {
      return []
    }
  }

  _filterSuggestionsForInput = (methods, inputValue) => {
    return _.filter(methods, (method) => {
      return _.includes(method.methodName.toLowerCase(), inputValue.toLowerCase())
    })
  }

  _findRootClazz = (properties) => {
    const variableName = properties[0]
    if (_.has(this._variables, variableName)) {
      const variableClazzName = _.get(this._variables, variableName)
      return _.reduce(_.tail(properties), (currentParentClazz, prop) => {
        const parentClazz = this._getTypeInfo(currentParentClazz)
        return _.get(parentClazz.methods, `${prop}.refClazzName`) || ""
      }, variableClazzName)
    } else {
      return null
    }
  }

  _getTypeInfo = (clazzName) => {
    const typeInfo = _.find(this._typesInformation, { clazzName: { refClazzName: clazzName }})
    return !_.isEmpty(typeInfo) ? typeInfo : {
      clazzName: {
        refClazzName: clazzName,
        methods: []
      }}
  }

  _focusedLastExpressionPartWithoutMethodParens = (expression, caretPosition) => {
    return this._lastExpressionPartWithoutMethodParens(this._currentlyFocusedExpressionPart(expression, caretPosition))
  }

  _focusedLastExpressionPart = (expression, caretPosition) => {
    return this._lastExpressionPart(this._currentlyFocusedExpressionPart(expression, caretPosition))
  }

  _currentlyFocusedExpressionPart = (value, caretPosition) => {
    return value.slice(0, caretPosition)
  }

  _lastExpressionPartWithoutMethodParens = (value) => {
    const valueCleaned = this._removeMethodParensFromProperty(value)
    return _.isEmpty(value) ? "" : "#" + _.last(_.split(valueCleaned, '#'))
  }

  _lastExpressionPart = (value) => {
    return _.isEmpty(value) ? "" : "#" + _.last(_.split(value, '#'))
  }

  _justTypedProperty = (value) => {
    return _.last(this._dotSeparatedToProperties(value))
  }

  _alreadyTypedProperties = (value) => {
    return _.initial(this._dotSeparatedToProperties(value))
  }

  _dotSeparatedToProperties = (value) => {
    return _.split(value, ".")
  }

  _propertiesToDotSeparated = (properties) => {
    return _.join(properties, ".")
  }

  _removeMethodParensFromProperty = (property) => {
    return property.replace(/\(.*\)/, "")
  }

}