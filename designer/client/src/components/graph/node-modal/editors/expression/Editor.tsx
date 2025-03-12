import { BoolEditor } from "./BoolEditor";
import { SpelEditor } from "./SpelEditor";
import { SqlEditor } from "./SqlEditor";
import { StringEditor } from "./StringEditor";
import { FixedValuesEditor } from "./FixedValuesEditor";
import { ExpressionLang, ExpressionObj } from "./types";
import React, { ForwardRefExoticComponent, LegacyRef, ReactNode } from "react";
import { DateEditor, DateTimeEditor, TimeEditor } from "./DateTimeEditor";

import { DurationEditor } from "./Duration/DurationEditor";
import { PeriodEditor } from "./Duration/PeriodEditor";
import { CronEditor } from "./Cron/CronEditor";
import { TextareaEditor } from "./TextareaEditor";
import JsonEditor from "./JsonEditor";
import { SpelTemplateEditor } from "./SpelTemplateEditor";
import { Formatter } from "./Formatter";
import { VariableTypes } from "../../../../../types";
import { FieldError } from "../Validators";
import { TableEditor } from "./Table/TableEditor";
import { DictParameterEditor } from "./DictParameterEditor";

export type EditorProps = {
    onValueChange: OnValueChange;
    type?: EditorType;
    editorConfig?: Record<string, unknown>;
    className?: string;
    fieldErrors: FieldError[];
    formatter?: Formatter;
    expressionInfo?: ReactNode;
    expressionObj: ExpressionObj;
    readOnly?: boolean;
    showSwitch?: boolean;
    showValidation?: boolean;
    variableTypes?: VariableTypes;
    ref?: LegacyRef<unknown>;
    rows?: number;
};

export type SimpleEditor<P extends EditorProps = EditorProps> =
    | React.ComponentType<P & EditorProps>
    | ForwardRefExoticComponent<P & EditorProps>;

export type ExtendedEditor<P extends EditorProps = EditorProps> = SimpleEditor<P> & {
    isSwitchableTo: (expressionObj: ExpressionObj, editorConfig) => boolean;
    switchableToHint: () => string;
    notSwitchableToHint: () => string;
    getExpressionMode?: (expressionObj: ExpressionObj) => ExpressionObj;
    getBasicMode?: (expressionObj: ExpressionObj) => ExpressionObj;
};

export function isExtendedEditor(editor: SimpleEditor | ExtendedEditor): editor is ExtendedEditor {
    return (editor as ExtendedEditor).isSwitchableTo !== undefined;
}

export enum DualEditorMode {
    SIMPLE = "SIMPLE",
    RAW = "RAW",
}

export enum EditorType {
    SPEL_PARAMETER_EDITOR = "SpelParameterEditor",
    BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
    STRING_PARAMETER_EDITOR = "StringParameterEditor",
    FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
    FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR = "FixedValuesWithIconParameterEditor",
    FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR = "FixedValuesWithRadioParameterEditor",
    DATE = "DateParameterEditor",
    TIME = "TimeParameterEditor",
    DATE_TIME = "DateTimeParameterEditor",
    DURATION_EDITOR = "DurationParameterEditor",
    PERIOD_EDITOR = "PeriodParameterEditor",
    CRON_EDITOR = "CronParameterEditor",
    TEXTAREA_PARAMETER_EDITOR = "TextareaParameterEditor",
    JSON_PARAMETER_EDITOR = "JsonParameterEditor",
    SQL_PARAMETER_EDITOR = "SqlParameterEditor",
    SPEL_TEMPLATE_PARAMETER_EDITOR = "SpelTemplateParameterEditor",
    DICT_PARAMETER_EDITOR = "DictParameterEditor",
    TABLE_EDITOR = "TabularTypedDataEditor",
}

export const editors: Record<EditorType, { component: SimpleEditor | ExtendedEditor; displayName: string; language: ExpressionLang }> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: { component: BoolEditor, displayName: "Boolean", language: ExpressionLang.SpEL },
    [EditorType.CRON_EDITOR]: { component: CronEditor, displayName: "Cron", language: ExpressionLang.SpEL },
    [EditorType.DATE]: { component: DateEditor, displayName: "Date", language: ExpressionLang.SpEL },
    [EditorType.DATE_TIME]: { component: DateTimeEditor, displayName: "Datetime", language: ExpressionLang.SpEL },
    [EditorType.DURATION_EDITOR]: { component: DurationEditor, displayName: "Duration", language: ExpressionLang.SpEL },
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: {
        component: FixedValuesEditor,
        displayName: "Fixed Values",
        language: ExpressionLang.SpEL,
    },
    [EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR]: {
        component: FixedValuesEditor,
        displayName: "Fixed Values",
        language: ExpressionLang.SpEL,
    },
    [EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR]: {
        component: FixedValuesEditor,
        displayName: "Radio",
        language: ExpressionLang.SpEL,
    },
    [EditorType.JSON_PARAMETER_EDITOR]: { component: JsonEditor, displayName: "Json", language: ExpressionLang.JSON },
    [EditorType.PERIOD_EDITOR]: { component: PeriodEditor, displayName: "Period", language: ExpressionLang.SpEL },
    [EditorType.SPEL_PARAMETER_EDITOR]: { component: SpelEditor, displayName: "Expression", language: ExpressionLang.SpEL },
    [EditorType.STRING_PARAMETER_EDITOR]: { component: StringEditor, displayName: "Text", language: ExpressionLang.String },
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: { component: TextareaEditor, displayName: "Textarea", language: ExpressionLang.SpEL },
    [EditorType.TIME]: { component: TimeEditor, displayName: "Time", language: ExpressionLang.SpEL },
    [EditorType.SQL_PARAMETER_EDITOR]: { component: SqlEditor, displayName: "SQL", language: ExpressionLang.SQL },
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: {
        component: SpelTemplateEditor,
        displayName: "String Template",
        language: ExpressionLang.SpELTemplate,
    },
    [EditorType.DICT_PARAMETER_EDITOR]: {
        component: DictParameterEditor,
        displayName: "Dictionary",
        language: ExpressionLang.DictKeyWithLabel,
    },
    [EditorType.TABLE_EDITOR]: { component: TableEditor, displayName: "Table", language: ExpressionLang.TabularDataDefinition },
};

export type OnValueChange = {
    (expression: ExpressionObj): void;
};
