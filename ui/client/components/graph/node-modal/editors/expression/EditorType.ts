import BoolEditor from "./BoolEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash";

type Editor = {
  editor: Function,
  hint: Function,
  showSwitch: boolean,
  switchableTo?: Function,
  values?: Function
  switchable?: Function,
}

export enum dualEditorMode {
  SIMPLE = "SIMPLE",
  RAW = "RAW",
}

export enum editorTypes {
  RAW_PARAMETER_EDITOR = "RawParameterEditor",
  BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
  STRING_PARAMETER_EDITOR = "StringParameterEditor",
  FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
  DUAL_PARAMETER_EDITOR = "DualParameterEditor",
}

export const editors: Record<editorTypes, Editor> = {
  [editorTypes.RAW_PARAMETER_EDITOR]: {
    editor: () => RawEditor,
    hint: () => "Switch to expression mode",
    showSwitch: false,
  },
  [editorTypes.BOOL_PARAMETER_EDITOR]: {
    editor: () => BoolEditor,
    hint: (switchable) => switchable ? BoolEditor.switchableToHint : BoolEditor.notSwitchableToHint,
    showSwitch: false,
    switchableTo: (expressionObj) => BoolEditor.switchableTo(expressionObj),
  },
  [editorTypes.STRING_PARAMETER_EDITOR]: {
    editor: () => StringEditor,
    hint: (switchable) => switchable ? StringEditor.switchableToHint : StringEditor.notSwitchableToHint,
    showSwitch: false,
    switchableTo: (expressionObj) => StringEditor.switchableTo(expressionObj),
  },
  [editorTypes.FIXED_VALUES_PARAMETER_EDITOR]: {
    editor: () => FixedValuesEditor,
    hint: (switchable) => switchable ? FixedValuesEditor.switchableToHint : FixedValuesEditor.notSwitchableToHint,
    showSwitch: false,
    switchableTo: (expressionObj, param, values) => FixedValuesEditor.switchableTo(expressionObj, !_.isEmpty(values) ? values : param.editor.possibleValues),
    values: (param, values) => !_.isEmpty(values) ? values : param.editor.possibleValues,
  },
  [editorTypes.DUAL_PARAMETER_EDITOR]: {
    editor: (param, displayRawEditor) => displayRawEditor ?
      editors[editorTypes.RAW_PARAMETER_EDITOR].editor() :
      editors[param.editor.simpleEditor.type].editor(),
    hint: (switchable, currentEditor, param) => currentEditor === RawEditor ?
      editors[param.editor.simpleEditor.type].hint(switchable) :
      editors[editorTypes.RAW_PARAMETER_EDITOR].hint(),
    showSwitch: true,
    switchable: (editor, param, expressionObj) => editor === RawEditor ?
      editors[param.editor.simpleEditor.type].switchableTo(expressionObj) :
      true,
    values: (param) => param.editor.simpleEditor.possibleValues,
  },
}
