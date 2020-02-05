import BoolEditor from "./BoolEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import _ from "lodash"
import {ExpressionObj} from "./types"
import i18next from "i18next"
import React from "react"
import {DateEditor} from "./DateTimeEditor/DateEditor"
import {TimeEditor} from "./DateTimeEditor/TimeEditor"
import {DateTimeEditor} from "./DateTimeEditor/DateTimeEditor"

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
  showSwitch?: boolean,
  switchableTo?: (expressionObj: ExpressionObj, param?: ParamType, values?: ValuesType) => boolean,
  values?: (param: ParamType, values: ValuesType) => $TodoType,
  switchable?: Function,
  validators?: Function,
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
  DATE = "DateParameterEditor",
  TIME = "TimeParameterEditor",
  DATE_TIME = "DateTimeParameterEditor",
  DUAL_PARAMETER_EDITOR = "DualParameterEditor",
}

const simpleEditorValidators = (param: $TodoType, errors: Array<$TodoType>, fieldLabel: string): Array<Validator> =>
  _.concat(
    param === undefined ?
      validators[validatorType.MANDATORY_VALUE_VALIDATOR]() :
      param.validators.map(validator => validators[validator.type]()),
    validators[validatorType.ERROR_VALIDATOR](errors, fieldLabel)
  )

export const editors: Record<editorTypes, Editor> = {
/* eslint-enable i18next/no-literal-string */

export const editors: Record<editorTypes, EditorConfig> = {
  [editorTypes.RAW_PARAMETER_EDITOR]: {
    editor: () => RawEditor,
    hint: () => i18next.t("editors.raw.switchableToHint", "Switch to expression mode"),
    validators: (param, errors, fieldLabel) => simpleEditorValidators(param, errors, fieldLabel),
  },
  [editorTypes.BOOL_PARAMETER_EDITOR]: {
    editor: () => BoolEditor,
    hint: (switchable) => switchable ? BoolEditor.switchableToHint : BoolEditor.notSwitchableToHint,
    switchableTo: (expressionObj) => BoolEditor.switchableTo(expressionObj),
    validators: (param, errors, fieldLabel) => simpleEditorValidators(param, errors, fieldLabel),
  },
  [editorTypes.STRING_PARAMETER_EDITOR]: {
    editor: () => StringEditor,
    hint: (switchable) => switchable ? StringEditor.switchableToHint : StringEditor.notSwitchableToHint,
    switchableTo: (expressionObj) => StringEditor.switchableTo(expressionObj),
    validators: (param, errors, fieldLabel) => simpleEditorValidators(param, errors, fieldLabel),
  },
  [editorTypes.FIXED_VALUES_PARAMETER_EDITOR]: {
    editor: () => FixedValuesEditor,
    hint: (switchable) => switchable ? FixedValuesEditor.switchableToHint : FixedValuesEditor.notSwitchableToHint,
    switchableTo: (expressionObj, param, values) => FixedValuesEditor.switchableTo(
      expressionObj,
      !_.isEmpty(values) ? values : param.editor.possibleValues,
    ),
    values: (param, values) => !_.isEmpty(values) ? values : param.editor.possibleValues,
    validators: (param, errors, fieldLabel) => simpleEditorValidators(param, errors, fieldLabel),
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
    validators: (param, errors, fieldLabel, displayRawEditor) => displayRawEditor ?
      editors[editorTypes.RAW_PARAMETER_EDITOR].validators(param, errors, fieldLabel) :
      editors[param.editor.simpleEditor.type].validators(param, errors, fieldLabel),
  },
  [editorTypes.DATE]: {
    editor: () => DateEditor,
    hint: switchable => switchable ? DateEditor.switchableToHint : DateEditor.notSwitchableToHint,
    switchableTo: DateEditor.switchableTo,
  },
  [editorTypes.TIME]: {
    editor: () => TimeEditor,
    hint: switchable => switchable ? TimeEditor.switchableToHint : TimeEditor.notSwitchableToHint,
    switchableTo: TimeEditor.switchableTo,
  },
  [editorTypes.DATE_TIME]: {
    editor: () => DateTimeEditor,
    hint: switchable => switchable ? DateTimeEditor.switchableToHint : DateTimeEditor.notSwitchableToHint,
    switchableTo: DateTimeEditor.switchableTo,
  },
}
