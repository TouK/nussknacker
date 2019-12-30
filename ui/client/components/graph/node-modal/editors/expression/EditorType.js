import RawEditor from "./RawEditor"
import BoolEditor, {parseableBoolean} from "./BoolEditor"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import StringEditor, {isEmpty, parseableString} from "./StringEditor"

class EditorType {

  isSupported = (filedType) => supportedFieldType.includes(filedType)

  editorName = (fieldType, expressionObj, displayRawEditor) => {
    switch (fieldType) {
      case Types.BOOLEAN:
        return !displayRawEditor && this.switchableToBooleanEditor(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.STRING:
        return !displayRawEditor && this.switchableToStringEditor(expressionObj) ? Types.STRING_EDITOR : Types.RAW_EDITOR
      case Types.EXPRESSION_WITH_FIXED_VALUES || Types.RAW_EDITOR:
        return fieldType
      default:
        return Types.RAW_EDITOR
    }
  }

  switchable = (editorName, expressionObj, fieldType) => {
    switch (editorName) {
      case Types.BOOL_EDITOR:
        return true
      case Types.RAW_EDITOR:
        return (fieldType === Types.BOOLEAN && this.switchableToBooleanEditor(expressionObj))
          || (fieldType === Types.EXPRESSION && this.switchableToBooleanEditor(expressionObj))
          || (fieldType === Types.STRING && this.switchableToStringEditor(expressionObj))
      case Types.STRING_EDITOR:
        return true
      default:
        return false
    }
  }

  switchableToBooleanEditor = (expressionObj) => parseableBoolean(expressionObj) || _.isEmpty(expressionObj.expression)

  switchableToStringEditor = (expressionObj) => parseableString(expressionObj) || _.isEmpty(expressionObj.expression)

  editor = (editorName) => _.get(editorTypes, editorName, editorTypes[Types.RAW_EDITOR])
}

export const Types = {
  BOOL_EDITOR: "boolEditor",
  STRING_EDITOR: "stringEditor",
  RAW_EDITOR: "rawEditor",
  BOOLEAN: "Boolean",
  STRING: "String",
  EXPRESSION_WITH_FIXED_VALUES: "expressionWithFixedValues",
}

const supportedFieldType = [Types.BOOLEAN, Types.STRING]

const editorTypes = {
  [Types.RAW_EDITOR]: RawEditor,
  [Types.BOOL_EDITOR]: BoolEditor,
  [Types.EXPRESSION_WITH_FIXED_VALUES]: ExpressionWithFixedValues,
  [Types.STRING_EDITOR]: StringEditor
}

export const editorType = new EditorType()