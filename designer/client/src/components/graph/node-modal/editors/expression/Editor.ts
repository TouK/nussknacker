import type { ForwardRefExoticComponent, LegacyRef, ReactNode } from "react";

import type { VariableTypes } from "../../../../../types/validation";
import type { FieldError } from "../Validators";
import { BoolEditor } from "./BoolEditor";
import { CronEditor } from "./Cron/CronEditor";
import { DateEditor } from "./DateTimeEditor/DateEditor";
import { DateTimeEditor } from "./DateTimeEditor/DateTimeEditor";
import { TimeEditor } from "./DateTimeEditor/TimeEditor";
import { DictParameterEditor } from "./DictParameterEditor/DictParameterEditor";
import { DurationEditor } from "./Duration/DurationEditor";
import { PeriodEditor } from "./Duration/PeriodEditor";
import type { EditorConfig } from "./EditorConfig";
import { FixedValuesEditor } from "./FixedValuesEditor";
import type { Formatter } from "./Formatter";
import { JsonEditor } from "./JsonEditor";
import { JsonTemplateEditor } from "./JsonTemplateEditor";
import { SpelEditor } from "./SpelEditor";
import { SpelTemplateEditor } from "./SpelTemplateEditor";
import { SqlEditor } from "./SqlEditor";
import { StaticStringEditor } from "./StaticStringEditor";
import { TableEditor } from "./Table/TableEditor";
import { TextareaEditor } from "./TextareaEditor";
import type { ExpressionLang, ExpressionObj } from "./types";
import { EditorType } from "./types";

export type EditorProps = {
    onValueChange: OnValueChange;
    type?: EditorType;
    editorConfig?: EditorConfig;
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
    isSwitchableTo: (expressionObj: P["expressionObj"], editorConfig: P["editorConfig"]) => boolean;
    parseValueOnEditorChange?: (expressionObject: ExpressionObj, newLanguage: ExpressionLang) => ExpressionObj;
    notSwitchableToHint: () => string;
};

export function isExtendedEditor(editor: SimpleEditor | ExtendedEditor): editor is ExtendedEditor {
    return (editor as ExtendedEditor)?.isSwitchableTo !== undefined;
}

export const editors: Record<EditorType, SimpleEditor | ExtendedEditor> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: BoolEditor,
    [EditorType.CRON_EDITOR]: CronEditor,
    [EditorType.DATE]: DateEditor,
    [EditorType.DATE_TIME]: DateTimeEditor,
    [EditorType.DURATION_EDITOR]: DurationEditor,
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR]: FixedValuesEditor,
    [EditorType.JSON_PARAMETER_EDITOR]: JsonEditor,
    [EditorType.PERIOD_EDITOR]: PeriodEditor,
    [EditorType.SPEL_PARAMETER_EDITOR]: SpelEditor,
    [EditorType.STATIC_STRING_PARAMETER_EDITOR]: StaticStringEditor,
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: TextareaEditor,
    [EditorType.TIME]: TimeEditor,
    [EditorType.SQL_PARAMETER_EDITOR]: SqlEditor,
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: SpelTemplateEditor,
    [EditorType.DICT_PARAMETER_EDITOR]: DictParameterEditor,
    [EditorType.TABLE_EDITOR]: TableEditor,
    [EditorType.JSON_TEMPLATE_PARAMETER_EDITOR]: JsonTemplateEditor,
};

export type OnValueChange = {
    (expression: ExpressionObj): void;
};
