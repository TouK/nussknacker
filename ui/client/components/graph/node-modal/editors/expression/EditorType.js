import BoolEditor from "./BoolEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"

class EditorType {
  basicEditorSupported = (fieldType) => Object.entries(editors).some(
    ([key, value]) => value.isSupported(fieldType)
      && key !== EditorTypes.RAW_EDITOR
      && value.switchableToEditors.length > 0,
  )
}

export const EditorTypes = [
  {
    type: "RawParameterEditor",
    showSwitch: (_) => false,
    editorName: (_) => SimpleEditorTypes.RAW_EDITOR,
    values: (_) => undefined,
  },
  {
    type: "SimpleParameterEditor",
    showSwitch: (_) => false,
    editorName: (param, values) => (!_.isEmpty(_.get(param, "restriction")) || !_.isEmpty(values)) ?
            SimpleEditorTypes.FIXED_VALUES_EDITOR :
            SimpleEditorTypes[param.editor.simpleEditorType],
    values: (param, values) => !_.isEmpty(values) ?
            values :
            (!_.isEmpty(_.get(param, "restriction")) ? param.restriction.values : param.editor.possibleValues),
  },
  {
    type: "DualParameterEditor",
    showSwitch: (_) => true,
    editorName: (param, _, displayRawEditor) => displayRawEditor === undefined ?
      (param.editor.defaultMode === DualEditorMode.SIMPLE ? SimpleEditorTypes[param.editor.simpleEditor.simpleEditorType] : SimpleEditorTypes.RAW_EDITOR) :
      (displayRawEditor ? SimpleEditorTypes.RAW_EDITOR : SimpleEditorTypes[param.editor.simpleEditor.simpleEditorType]),
    values: (param) => param.editor.simpleEditor.possibleValues,
  }
]

export const DualEditorMode = {
  SIMPLE: "SIMPLE",
  RAW: "RAW"
}

export const SimpleEditorTypes = {
  BOOL_EDITOR: "boolEditor",
  STRING_EDITOR: "stringEditor",
  RAW_EDITOR: "rawEditor",
  EXPRESSION: "expression",
  FIXED_VALUES_EDITOR: "fixedValuesEditor",
}

export const editors = {
  [SimpleEditorTypes.RAW_EDITOR]: {
    editor: RawEditor,
    switchableToEditors: [SimpleEditorTypes.BOOL_EDITOR, SimpleEditorTypes.STRING_EDITOR],
    switchableTo: (_) => RawEditor.switchableTo(),
    switchableToHint: RawEditor.switchableToHint,
    isSupported: (fieldType) => RawEditor.supportedFieldTypes.includes(fieldType),
  },
  [SimpleEditorTypes.BOOL_EDITOR]: {
    editor: BoolEditor,
    switchableToEditors: [SimpleEditorTypes.RAW_EDITOR],
    switchableTo: (expression) => BoolEditor.switchableTo(expression),
    switchableToHint: BoolEditor.switchableToHint,
    notSwitchableToHint: BoolEditor.notSwitchableToHint,
    isSupported: (fieldType) => BoolEditor.isSupported(fieldType),
  },
  [SimpleEditorTypes.STRING_EDITOR]: {
    editor: StringEditor,
    switchableToEditors: [SimpleEditorTypes.RAW_EDITOR],
    switchableTo: (expression) => StringEditor.switchableTo(expression),
    switchableToHint: StringEditor.switchableToHint,
    notSwitchableToHint: StringEditor.notSwitchableToHint,
    isSupported: (fieldType) => StringEditor.isSupported(fieldType),
  },
  [SimpleEditorTypes.FIXED_VALUES_EDITOR]: {
    editor: FixedValuesEditor,
    switchableToEditors: [],
    isSupported: (fieldType) => FixedValuesEditor.isSupported(fieldType),
  },
}

export const editorType = new EditorType()
