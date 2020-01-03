import RawEditor from "./RawEditor"
import BoolEditor from "./BoolEditor"
import StringEditor from "./StringEditor"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"

class EditorType {

  isSupported = (fieldType) => Object.values(editors).map(value => value.supportedFieldType).includes(fieldType)

  editor = (editorName) => _.get(editors, editorName, editors[Types.RAW_EDITOR])
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

const editors = {
  [Types.RAW_EDITOR]: {
    editor: RawEditor,
    switchable: (editorName, expressionObj, fieldType) => RawEditor.switchableFrom(editorName, expressionObj, fieldType),
    switchableHint: (editorName, expressionObj, fieldType) => RawEditor.switchableFromHint(editorName, expressionObj, fieldType),
    supportedFieldType: Types.BOOLEAN
  },
  [Types.BOOL_EDITOR]: {
    editor: BoolEditor,
    switchable: BoolEditor.switchableFrom(),
    switchableHint: (_) => BoolEditor.switchableFromHint,
  },
  [Types.STRING_EDITOR]: {
    editor: StringEditor,
    switchable: BoolEditor.switchableFrom(),
    switchableHint: (_) => StringEditor.switchableFromHint,
    supportedFieldType: Types.STRING
  },
  [Types.EXPRESSION_WITH_FIXED_VALUES]: {
    editor: ExpressionWithFixedValues,
    switchable: false,
    switchableHint: null
  }
}

export const editorType = new EditorType()