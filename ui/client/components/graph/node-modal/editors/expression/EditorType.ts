import BoolEditor from "./BoolEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash"
import {ExpressionObj} from "./types"
import i18next from "i18next"
import React from "react"

type ParamType = $TodoType
type ValuesType = $TodoType
type EditorProps = $TodoType

export type EditorType<P extends EditorProps = EditorProps> = React.ComponentType<P> & {
  switchableTo: (expressionObj: ExpressionObj, values?: ValuesType) => boolean,
  switchableToHint: string,
  notSwitchableToHint: string,
}

type EditorConfig = {
  editor: (param?: ParamType, displayRawEditor?) => EditorType,
  hint: (switchable?: boolean, currentEditor?: EditorType, param?: ParamType) => string,
  showSwitch: boolean,
  switchableTo?: (expressionObj: ExpressionObj, param?: ParamType, values?: ValuesType) => boolean,
  values?: (param: ParamType, values: ValuesType) => $TodoType,
  switchable?: Function,
}

export enum dualEditorMode {
  SIMPLE = "SIMPLE",
  RAW = "RAW",
}

/* eslint-disable i18next/no-literal-string */
export enum editorTypes {
  RAW_PARAMETER_EDITOR = "RawParameterEditor",
  BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
  STRING_PARAMETER_EDITOR = "StringParameterEditor",
  FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
  DUAL_PARAMETER_EDITOR = "DualParameterEditor",
}

/* eslint-enable i18next/no-literal-string */

export const editors: Record<editorTypes, EditorConfig> = {
  [editorTypes.RAW_PARAMETER_EDITOR]: {
    editor: () => RawEditor,
    hint: () => i18next.t("editors.raw.hint", "Switch to expression mode"),
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
    switchableTo: (expressionObj, param, values) => FixedValuesEditor.switchableTo(
        expressionObj,
        !_.isEmpty(values) ? values : param.editor.possibleValues,
    ),
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
