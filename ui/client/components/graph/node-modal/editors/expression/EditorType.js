import {parseableBoolean} from "./ExpressionParser"
import RawEditor from "./RawEditor"
import BoolEditor from "./BoolEditor"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"

class EditorType {

  isSupported = (filedType) => supportedFieldType.includes(filedType)

  editorName = (fieldType, expressionObj, displayRawEditor) => {
    switch (fieldType) {
      case Types.EXPRESSION:
        return !displayRawEditor && this.switchableToBooleanEditor(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.BOOLEAN:
        return !displayRawEditor && this.switchableToBooleanEditor(expressionObj) ? Types.BOOL_EDITOR : Types.RAW_EDITOR
      case Types.EXPRESSION_WITH_FIXED_VALUES || Types.RAW_EDITOR:
        return fieldType
      default:
        return Types.RAW_EDITOR
    }
  }

  switchableToBooleanEditor = (expressionObj) => parseableBoolean(expressionObj) || _.isEmpty(expressionObj.expression)

  editor = (editorName) => _.get(editorTypes, editorName, editorTypes[Types.RAW_EDITOR])
}

export const Types = {
  BOOL_EDITOR: "boolEditor",
  RAW_EDITOR: "rawEditor",
  EXPRESSION: "expression",
  BOOLEAN: "Boolean",
  EXPRESSION_WITH_FIXED_VALUES: "expressionWithFixedValues",
}

const supportedFieldType = [Types.EXPRESSION, Types.BOOLEAN]

const editorTypes = {
  [Types.RAW_EDITOR]: RawEditor,
  [Types.BOOL_EDITOR]: BoolEditor,
  [Types.EXPRESSION_WITH_FIXED_VALUES]: ExpressionWithFixedValues
}

export const editorType = new EditorType()