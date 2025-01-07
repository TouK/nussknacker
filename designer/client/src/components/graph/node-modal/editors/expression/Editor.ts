import { BoolEditor } from "./BoolEditor";
import { RawEditor } from "./RawEditor";
import { SqlEditor } from "./SqlEditor";
import { StringEditor } from "./StringEditor";
import { FixedValuesEditor } from "./FixedValuesEditor";
import { ExpressionObj } from "./types";
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
    RAW_PARAMETER_EDITOR = "RawParameterEditor",
    BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
    STRING_PARAMETER_EDITOR = "StringParameterEditor",
    FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
    FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR = "FixedValuesWithIconParameterEditor",
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

export const editors: Record<EditorType, SimpleEditor | ExtendedEditor> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: BoolEditor,
    [EditorType.CRON_EDITOR]: CronEditor,
    [EditorType.DATE]: DateEditor,
    [EditorType.DATE_TIME]: DateTimeEditor,
    [EditorType.DURATION_EDITOR]: DurationEditor,
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.JSON_PARAMETER_EDITOR]: JsonEditor,
    [EditorType.PERIOD_EDITOR]: PeriodEditor,
    [EditorType.RAW_PARAMETER_EDITOR]: RawEditor,
    [EditorType.STRING_PARAMETER_EDITOR]: StringEditor,
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: TextareaEditor,
    [EditorType.TIME]: TimeEditor,
    [EditorType.SQL_PARAMETER_EDITOR]: SqlEditor,
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: SpelTemplateEditor,
    [EditorType.DICT_PARAMETER_EDITOR]: DictParameterEditor,
    [EditorType.TABLE_EDITOR]: TableEditor,
};

export const editorNames: Record<EditorType, { displayName: string; icon?: string }> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: { displayName: "Boolean" },
    [EditorType.CRON_EDITOR]: { displayName: "Cron" },
    [EditorType.DATE]: { displayName: "Date" },
    [EditorType.DATE_TIME]: { displayName: "Datetime" },
    [EditorType.DURATION_EDITOR]: { displayName: "Duration" },
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: { displayName: "Fixed Values" },
    [EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR]: { displayName: "Fixed Values" },
    [EditorType.JSON_PARAMETER_EDITOR]: { displayName: "Json" },
    [EditorType.PERIOD_EDITOR]: { displayName: "Period" },
    [EditorType.RAW_PARAMETER_EDITOR]: { displayName: "SpEL" },
    [EditorType.STRING_PARAMETER_EDITOR]: { displayName: "String" },
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: { displayName: "Textarea" },
    [EditorType.TIME]: { displayName: "Time" },
    [EditorType.SQL_PARAMETER_EDITOR]: { displayName: "SQL" },
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: { displayName: "SpEL Template" },
    [EditorType.DICT_PARAMETER_EDITOR]: { displayName: "Dictionary" },
    [EditorType.TABLE_EDITOR]: { displayName: "Table" },
};

export type OnValueChange = {
    (expression: ExpressionObj | string): void;
};
