import type { ComponentType, LegacyRef, ReactNode } from "react";

import type { VariableTypes } from "../../../../../types/validation";
import type { Prettify } from "../../useNodeTypeDetailsContentLogic";
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
import type { PrintMissingProps, WithoutDeprecated } from "./EditorTypeHelpers";
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

type BaseEditorProps = {
    expressionObj: ExpressionObj;
    onValueChange: OnValueChange;
    fieldErrors: FieldError[];
    readOnly?: boolean;
    showSwitch?: boolean;
    showValidation?: boolean;
    className?: string;
    formatter?: Formatter;
};

export type CustomEditorProps = {
    editorConfig: EditorConfig;
    expressionInfo?: ReactNode;
    variableTypes?: VariableTypes;
    ref?: LegacyRef<unknown>;
    rows?: number;
};

export type EditorProps = BaseEditorProps & CustomEditorProps;

type SimpleEditor<P = EditorProps> = P extends EditorProps ? ComponentType<P & EditorProps> : PrintMissingProps<P>;

type Addons<P> = P extends EditorProps
    ? {
          isSwitchableTo: (expressionObj: P["expressionObj"], editorConfig: P["editorConfig"]) => boolean;
          notSwitchableToHint: () => string;
          parseValueOnEditorChange?: (expressionObj: P["expressionObj"], nextLanguage: ExpressionLang) => ExpressionObj;
      }
    : PrintMissingProps<P>;

type ExtendedEditor<P = EditorProps> = SimpleEditor<P> & Addons<P>;

type Cleaned<P> = WithoutDeprecated<P, BaseEditorProps>;

export function prepareEditor<P>(Component: SimpleEditor<Cleaned<P>>): SimpleEditor<Prettify<P & Cleaned<P>>>;
export function prepareEditor<P>(Component: SimpleEditor<Cleaned<P>>, addons: Addons<Cleaned<P>>): ExtendedEditor<Prettify<P & Cleaned<P>>>;
export function prepareEditor<P>(
    Component: SimpleEditor<Cleaned<P>>,
    addons?: Addons<Cleaned<P>>,
): SimpleEditor<P & Cleaned<P>> | ExtendedEditor<P & Cleaned<P>> {
    if (!addons) return Component as SimpleEditor<P & Cleaned<P>>;
    return Object.assign(Component, addons) as ExtendedEditor<P & Cleaned<P>>;
}

export function isExtendedEditor(editor: SimpleEditor | ExtendedEditor): editor is ExtendedEditor {
    return "isSwitchableTo" in editor;
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
