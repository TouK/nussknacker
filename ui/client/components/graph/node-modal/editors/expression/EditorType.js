import RawEditor from "./RawEditor"
import BoolEditor from "./BoolEditor"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import StringEditor from "./StringEditor"

class EditorType {

  isSupported = (filedType) => supportedFieldType.includes(filedType)

  editor = (editorName) => _.get(editorTypes, editorName, editorTypes[Types.RAW_EDITOR])
}

export const Types = {
  BOOL_EDITOR: "boolEditor",
  STRING_EDITOR: "stringEditor",
  RAW_EDITOR: "rawEditor",
  EXPRESSION: "expression",
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