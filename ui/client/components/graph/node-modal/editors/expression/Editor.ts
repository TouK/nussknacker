import BoolEditor from "./BoolEditor"
import RawEditor from "./RawEditor"
import StringEditor from "./StringEditor"
import FixedValuesEditor from "./FixedValuesEditor"
import {concat, isEmpty, omit} from "lodash"
import {ExpressionObj} from "./types"
import i18next from "i18next"
import React from "react"
import {DateEditor} from "./DateTimeEditor/DateEditor"
import {TimeEditor} from "./DateTimeEditor/TimeEditor"
import {DateTimeEditor} from "./DateTimeEditor/DateTimeEditor"
import {
  Error,
  errorValidator,
  mandatoryValueValidator,
  notBlankValueValidator,
  Validator,
  validators,
} from "../Validators"
import DurationEditor from "./Duration/DurationEditor"
import PeriodEditor from "./Duration/PeriodEditor"
import CronEditor from "./Cron/CronEditor"
import {components, TimeRangeComponentType} from "./Duration/TimeRangeComponent"
import TextareaEditor from "./TextareaEditor"
import JsonEditor from "./JsonEditor"

type ParamType = $TodoType
type ValuesType = Array<string>
type EditorProps = $TodoType

export type Editor<P extends EditorProps = EditorProps> = React.ComponentType<P> & {
  switchableTo: (expressionObj: ExpressionObj, values?: ValuesType) => boolean,
  switchableToHint: () => string,
  notSwitchableToHint: () => string,
}

type EditorConfig = {
  editor: (param?: ParamType, displayRawEditor?: boolean, expressionObj?: ExpressionObj) => Editor,
  hint: (switchable?: boolean, currentEditor?: Editor, param?: ParamType) => string,
  showSwitch?: boolean,
  switchableTo?: (expressionObj: ExpressionObj, param?: ParamType, values?: ValuesType) => boolean,
  values?: (param: ParamType, values: ValuesType) => $TodoType,
  switchable?: (editor: Editor, param: ParamType, expressionObj: ExpressionObj) => boolean,
  validators: (param: ParamType, errors: Array<Error>, fieldLabel: string, displayRawEditor?: boolean) => Array<Validator>,
  components: (param: ParamType) => Array<TimeRangeComponentType>,
}

/* eslint-enable i18next/no-literal-string */
export enum DualEditorMode {
  SIMPLE = "SIMPLE",
  RAW = "RAW",
}

/* eslint-disable i18next/no-literal-string */
export enum EditorType {
  RAW_PARAMETER_EDITOR = "RawParameterEditor",
  BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
  STRING_PARAMETER_EDITOR = "StringParameterEditor",
  FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
  DATE = "DateParameterEditor",
  TIME = "TimeParameterEditor",
  DATE_TIME = "DateTimeParameterEditor",
  DUAL_PARAMETER_EDITOR = "DualParameterEditor",
  DURATION_EDITOR = "DurationParameterEditor",
  PERIOD_EDITOR = "PeriodParameterEditor",
  CRON_EDITOR = "CronParameterEditor",
  TEXTAREA_PARAMETER_EDITOR = "TextareaParameterEditor",
  JSON_PARAMETER_EDITOR = "JsonParameterEditor",
}

const configureValidators = (paramConfig: $TodoType): Array<Validator> => {
  //It's for special nodes like Filter, Switch, etc.. These nodes don't have params and all fields are required
  if (paramConfig == null) {
    return [
      mandatoryValueValidator,
    ]
  }

  //Try to create validators with args - all configuration is from BE. It's dynamic mapping
  return (paramConfig.validators || [])
    .map(v => ({fun: validators[v.type], args: omit(v, ["type"])}))
    .filter(v => v.fun != null)
    .map(v => v.fun(v.args))
}

const simpleEditorValidators = (paramConfig: $TodoType, errors: Array<Error>, fieldLabel: string): Array<Validator> => {
  const configuredValidators = configureValidators(paramConfig)
  const validatorFromErrors = isEmpty(errors) ? [] : [errorValidator(errors, fieldLabel)]
  return concat(
    configuredValidators,
    validatorFromErrors,
  )
}

const defaults = {
  validators: (param, errors, fieldLabel) => simpleEditorValidators(param, errors, fieldLabel),
  components: () => null,
}

export const editors: Record<EditorType, EditorConfig> = {
  [EditorType.RAW_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => RawEditor,
    hint: () => i18next.t("editors.raw.switchableToHint", "Switch to expression mode"),
  },
  [EditorType.BOOL_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => BoolEditor,
    hint: (switchable) => switchable ? BoolEditor.switchableToHint() : BoolEditor.notSwitchableToHint(),
    switchableTo: (expressionObj) => BoolEditor.switchableTo(expressionObj),
  },
  [EditorType.STRING_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => StringEditor,
    hint: (switchable) => switchable ? StringEditor.switchableToHint() : StringEditor.notSwitchableToHint(),
    switchableTo: (expressionObj) => StringEditor.switchableTo(expressionObj),
  },
  [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => FixedValuesEditor,
    hint: (switchable) => switchable ? FixedValuesEditor.switchableToHint() : FixedValuesEditor.notSwitchableToHint(),
    switchableTo: (expressionObj, param, values) => FixedValuesEditor.switchableTo(
      expressionObj,
      !isEmpty(values) ? values : param.editor.simpleEditor.possibleValues,
    ),
    values: (param, values) => !isEmpty(values) ? values : param.editor.possibleValues,
  },
  [EditorType.DUAL_PARAMETER_EDITOR]: {
    editor: (param, displayRawEditor, expressionObj) => displayRawEditor ?
      editors[EditorType.RAW_PARAMETER_EDITOR].editor() :
      editors[param.editor.simpleEditor.type].editor(param, displayRawEditor, expressionObj),
    hint: (switchable, currentEditor, param) => currentEditor === RawEditor ?
      editors[param.editor.simpleEditor.type].hint(switchable) :
      editors[EditorType.RAW_PARAMETER_EDITOR].hint(),
    showSwitch: true,
    switchable: (editor, param, expressionObj) => editor === RawEditor ?
      editors[param.editor.simpleEditor.type].switchableTo(expressionObj, param) :
      true,
    values: (param) => param.editor.simpleEditor.possibleValues,
    validators: (param, errors, fieldLabel, displayRawEditor) => displayRawEditor ?
      editors[EditorType.RAW_PARAMETER_EDITOR].validators(param, errors, fieldLabel) :
      editors[param.editor.simpleEditor.type].validators(param, errors, fieldLabel),
    components: (param) => param.editor.simpleEditor.timeRangeComponents ?
      param.editor.simpleEditor.timeRangeComponents.map(component => components[component]) :
      null,
  },
  [EditorType.DATE]: {
    ...defaults,
    editor: () => DateEditor,
    hint: switchable => switchable ? DateEditor.switchableToHint() : DateEditor.notSwitchableToHint(),
    switchableTo: DateEditor.switchableTo,
  },
  [EditorType.TIME]: {
    ...defaults,
    editor: () => TimeEditor,
    hint: switchable => switchable ? TimeEditor.switchableToHint() : TimeEditor.notSwitchableToHint(),
    switchableTo: TimeEditor.switchableTo,
  },
  [EditorType.DATE_TIME]: {
    ...defaults,
    editor: () => DateTimeEditor,
    hint: switchable => switchable ? DateTimeEditor.switchableToHint() : DateTimeEditor.notSwitchableToHint(),
    switchableTo: DateTimeEditor.switchableTo,
  },
  [EditorType.DURATION_EDITOR]: {
    ...defaults,
    editor: () => DurationEditor,
    hint: switchable => switchable ? DurationEditor.switchableToHint() : DurationEditor.notSwitchableToHint(),
    switchableTo: DurationEditor.switchableTo,
    components: (param) => param.editor.timeRangeComponents ?
      param.editor.timeRangeComponents.map(component => components[component]) :
      null,
  },
  [EditorType.PERIOD_EDITOR]: {
    ...defaults,
    editor: () => PeriodEditor,
    hint: switchable => switchable ? PeriodEditor.switchableToHint() : PeriodEditor.notSwitchableToHint(),
    switchableTo: PeriodEditor.switchableTo,
    components: (param) => param.editor.timeRangeComponents ?
      param.editor.timeRangeComponents.map(component => components[component]) :
      null,
  },
  [EditorType.CRON_EDITOR]: {
    ...defaults,
    editor: () => CronEditor,
    hint: switchable => switchable ? CronEditor.switchableToHint() : CronEditor.notSwitchableToHint(),
    switchableTo: CronEditor.switchableTo,
  },
  [EditorType.TEXTAREA_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => TextareaEditor,
    hint: (switchable) => switchable ? TextareaEditor.switchableToHint() : TextareaEditor.notSwitchableToHint(),
    switchableTo: (expressionObj) => TextareaEditor.switchableTo(expressionObj),
  },
  [EditorType.JSON_PARAMETER_EDITOR]: {
    ...defaults,
    editor: () => JsonEditor,
    hint: (switchable) => switchable ? JsonEditor.switchableToHint() : JsonEditor.notSwitchableToHint(),
    switchableTo: (expressionObj) => JsonEditor.switchableTo(expressionObj),
  },
}
