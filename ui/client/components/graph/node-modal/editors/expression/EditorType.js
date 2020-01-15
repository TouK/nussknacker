import BoolEditor from "./BoolEditor"
import ExpressionWithFixedValues from "./ExpressionWithFixedValues"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"

class EditorType {
  basicEditorSupported = (fieldType) => Object.entries(editors).some(
    ([key, value]) => value.isSupported(fieldType)
      && key !== Types.RAW_EDITOR
      && value.switchableToEditors.length > 0,
  )
}

export const Types = {
  BOOL_EDITOR: "boolEditor",
  STRING_EDITOR: "stringEditor",
  RAW_EDITOR: "rawEditor",
  EXPRESSION: "expression",
  EXPRESSION_WITH_FIXED_VALUES: "expressionWithFixedValues",
}

export const editors = {
  [Types.RAW_EDITOR]: {
    editor: RawEditor,
    switchableToEditors: [Types.BOOL_EDITOR, Types.STRING_EDITOR],
    switchableTo: (_) => RawEditor.switchableTo(),
    switchableToHint: RawEditor.switchableToHint,
    isSupported: (fieldType) => RawEditor.supportedFieldTypes.includes(fieldType),
  },
  [Types.BOOL_EDITOR]: {
    editor: BoolEditor,
    switchableToEditors: [Types.RAW_EDITOR],
    switchableTo: (expression) => BoolEditor.switchableTo(expression),
    switchableToHint: BoolEditor.switchableToHint,
    notSwitchableToHint: BoolEditor.notSwitchableToHint,
    isSupported: (fieldType) => BoolEditor.isSupported(fieldType),
  },
  [Types.STRING_EDITOR]: {
    editor: StringEditor,
    switchableToEditors: [Types.RAW_EDITOR],
    switchableTo: (expression) => StringEditor.switchableTo(expression),
    switchableToHint: StringEditor.switchableToHint,
    notSwitchableToHint: StringEditor.notSwitchableToHint,
    isSupported: (fieldType) => StringEditor.isSupported(fieldType),
  },
  [Types.EXPRESSION_WITH_FIXED_VALUES]: {
    editor: ExpressionWithFixedValues,
    switchableToEditors: [],
    isSupported: (fieldType) => ExpressionWithFixedValues.isSupported(fieldType),
  },
}

export const editorType = new EditorType()
