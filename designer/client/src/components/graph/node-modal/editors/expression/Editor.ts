import { BoolEditor } from "./BoolEditor";
import { RawEditor } from "./RawEditor";
import { SqlEditor } from "./SqlEditor";
import { StringEditor } from "./StringEditor";
import { FixedValuesEditor } from "./FixedValuesEditor";
import { concat, isEmpty, omit } from "lodash";
import { ExpressionObj } from "./types";
import React, { ForwardRefExoticComponent } from "react";
import { DateEditor, DateTimeEditor, TimeEditor } from "./DateTimeEditor";

import { Error, errorValidator, mandatoryValueValidator, Validator, validators } from "../Validators";
import { DurationEditor } from "./Duration/DurationEditor";
import { PeriodEditor } from "./Duration/PeriodEditor";
import { CronEditor } from "./Cron/CronEditor";
import { TextareaEditor } from "./TextareaEditor";
import JsonEditor from "./JsonEditor";
import { DualParameterEditor } from "./DualParameterEditor";
import { SpelTemplateEditor } from "./SpelTemplateEditor";
import { Formatter } from "./Formatter";
import { VariableTypes } from "../../../../../types";

export type EditorProps = {
    onValueChange: (value: string) => void;
    type?: EditorType;
    editorConfig?: Record<string, unknown>;
    className?: string;
    validators?: Validator[];
    formatter?: Formatter;
    expressionInfo?: string;
    expressionObj?: ExpressionObj;
    readOnly?: boolean;
    showSwitch?: boolean;
    showValidation?: boolean;
    variableTypes?: VariableTypes;
    values?: string[];
};

export type SimpleEditor<P extends EditorProps = EditorProps> =
    | React.ComponentType<P & EditorProps>
    | ForwardRefExoticComponent<P & EditorProps>;

export type ExtendedEditor<P extends EditorProps = EditorProps> = SimpleEditor<P> & {
    isSwitchableTo: (expressionObj: ExpressionObj, editorConfig) => boolean;
    switchableToHint: () => string;
    notSwitchableToHint: () => string;
};

export function isExtendedEditor(editor: SimpleEditor | ExtendedEditor): editor is ExtendedEditor {
    return (editor as ExtendedEditor).isSwitchableTo !== undefined;
}

export enum DualEditorMode {
    SIMPLE = "SIMPLE",
    RAW = "RAW",
}

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
    SQL_PARAMETER_EDITOR = "SqlParameterEditor",
    SPEL_TEMPLATE_PARAMETER_EDITOR = "SpelTemplateParameterEditor",
}

const configureValidators = (paramConfig: $TodoType): Array<Validator> => {
    //It's for special nodes like Filter, Switch, etc.. These nodes don't have params and all fields are required
    if (paramConfig == null) {
        return [mandatoryValueValidator];
    }

    //Try to create validators with args - all configuration is from BE. It's dynamic mapping
    return (paramConfig.validators || [])
        .map((v) => ({ fun: validators[v.type], args: omit(v, ["type"]) }))
        .filter((v) => v.fun != null)
        .map((v) => v.fun(v.args));
};

export const simpleEditorValidators = (
    paramConfig: $TodoType,
    errors: Error[],
    fieldName: string,
    fieldLabel: string,
): Array<Validator> => {
    const configuredValidators = configureValidators(paramConfig);
    // Identifier or field is in one of places: fieldName or fieldLabel. Because of this we need to collect errors from both of them.
    // Especially for branch fields, "common" branch parameter identifier is in fieldLabel and identifier for specific branch is in fieldName.
    // We want to handle both error types: common branch parameter errors and errors for specific branch.
    const validatorFromErrorsForFieldName = fieldName == null || isEmpty(errors) ? [] : [errorValidator(errors, fieldName)];
    const validatorFromErrorsForFieldLabel =
        fieldLabel == null || fieldLabel == fieldName || isEmpty(errors) ? [] : [errorValidator(errors, fieldLabel)];
    return concat(configuredValidators, validatorFromErrorsForFieldName, validatorFromErrorsForFieldLabel);
};

export const editors: Record<EditorType, SimpleEditor | ExtendedEditor> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: BoolEditor,
    [EditorType.CRON_EDITOR]: CronEditor,
    [EditorType.DATE]: DateEditor,
    [EditorType.DATE_TIME]: DateTimeEditor,
    [EditorType.DUAL_PARAMETER_EDITOR]: DualParameterEditor,
    [EditorType.DURATION_EDITOR]: DurationEditor,
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.JSON_PARAMETER_EDITOR]: JsonEditor,
    [EditorType.PERIOD_EDITOR]: PeriodEditor,
    [EditorType.RAW_PARAMETER_EDITOR]: RawEditor,
    [EditorType.STRING_PARAMETER_EDITOR]: StringEditor,
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: TextareaEditor,
    [EditorType.TIME]: TimeEditor,
    [EditorType.SQL_PARAMETER_EDITOR]: SqlEditor,
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: SpelTemplateEditor,
};
