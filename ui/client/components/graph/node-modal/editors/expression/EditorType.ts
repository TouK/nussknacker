import BoolEditor from "./BoolEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash";

export enum dualEditorMode {
    SIMPLE = "SIMPLE",
    RAW = "RAW",
}

export enum editorTypes {
  RAW_PARAMETER_EDITOR =  "RawParameterEditor",
  BOOL_PARAMETER_EDITOR =  "BoolParameterEditor",
  STRING_PARAMETER_EDITOR =  "StringParameterEditor",
  FIXED_VALUES_PARAMETER_EDITOR =  "FixedValuesParameterEditor",
  DUAL_PARAMETER_EDITOR = "DualParameterEditor",
}

export const editors = {
    [editorTypes.RAW_PARAMETER_EDITOR]: {
        editor: () => RawEditor,
        hint: () => "Switch to expression mode",
        showSwitch: false,
    },
    [editorTypes.BOOL_PARAMETER_EDITOR]: {
        editor: () => BoolEditor,
        switchableTo: (expressionObj) => BoolEditor.switchableTo(expressionObj),
        hint: (switchable) => switchable ? BoolEditor.switchableToHint : BoolEditor.notSwitchableToHint,
        showSwitch: false,
    },
    [editorTypes.STRING_PARAMETER_EDITOR]: {
        editor: () => StringEditor,
        switchableTo: (expressionObj) => StringEditor.switchableTo(expressionObj),
        hint: (switchable) => switchable ? StringEditor.switchableToHint : StringEditor.notSwitchableToHint,
        showSwitch: false,
    },
    [editorTypes.FIXED_VALUES_PARAMETER_EDITOR]: {
        editor: () => FixedValuesEditor,
        switchableTo: (expressionObj, param, values) => FixedValuesEditor.switchableTo(expressionObj, !_.isEmpty(values) ? values : param.editor.possibleValues),
        hint: (switchable) => switchable ? FixedValuesEditor.switchableToHint : FixedValuesEditor.notSwitchableToHint,
        values: (param, values) => !_.isEmpty(values) ? values : param.editor.possibleValues,
        showSwitch: false,
    },
    [editorTypes.DUAL_PARAMETER_EDITOR]: {
        editor: (param, displayRawEditor) => displayRawEditor === undefined ?
            (param.editor.defaultMode === dualEditorMode.SIMPLE ? editors[param.editor.simpleEditor.type].editor() : editors[editorTypes.RAW_PARAMETER_EDITOR].editor()) :
            (displayRawEditor ? editors[editorTypes.RAW_PARAMETER_EDITOR].editor() : editors[param.editor.simpleEditor.type].editor()),
        values: (param) => param.editor.simpleEditor.possibleValues,
        showSwitch: true,
        switchable: (editor, param, expressionObj) => editor === RawEditor ?
            editors[param.editor.simpleEditor.type].switchableTo(expressionObj) :
            true,
        hint: (switchable, currentEditor, param) => currentEditor === RawEditor ?
            editors[param.editor.simpleEditor.type].hint(switchable) :
            editors[editorTypes.RAW_PARAMETER_EDITOR].hint(),
    },
}
