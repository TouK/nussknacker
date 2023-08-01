import { TaggedUnion } from "type-fest";

type ParameterEditor = NonNullable<unknown>;

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

type RawParameterEditor = ParameterEditor;

type SimpleParameterEditor = ParameterEditor;

type BoolParameterEditor = SimpleParameterEditor;

type StringParameterEditor = SimpleParameterEditor;

type DateParameterEditor = SimpleParameterEditor;

type TimeParameterEditor = SimpleParameterEditor;

type DateTimeParameterEditor = SimpleParameterEditor;

type TextareaParameterEditor = SimpleParameterEditor;

type JsonParameterEditor = SimpleParameterEditor;

type SqlParameterEditor = SimpleParameterEditor;

type SpelTemplateParameterEditor = SimpleParameterEditor;

export enum ChronoUnit {
    Years = "YEARS",
    Months = "MONTHS",
    Days = "DAYS",
    Hours = "HOURS",
    Minutes = "MINUTES",
}

type DurationParameterEditor = SimpleParameterEditor & {
    timeRangeComponents: ChronoUnit[];
};

type PeriodParameterEditor = SimpleParameterEditor & {
    timeRangeComponents: ChronoUnit[];
};

type CronParameterEditor = SimpleParameterEditor;

export type FixedExpressionValue = {
    expression: string;
    label: string;
};

type FixedValuesParameterEditor = SimpleParameterEditor & {
    possibleValues: FixedExpressionValue[];
};

export enum DualEditorMode {
    SIMPLE = "SIMPLE",
    RAW = "RAW",
}

export type SimpleEditorProps = TaggedUnion<
    "type",
    {
        [EditorType.BOOL_PARAMETER_EDITOR]: BoolParameterEditor;
        [EditorType.STRING_PARAMETER_EDITOR]: StringParameterEditor;
        [EditorType.DATE]: DateParameterEditor;
        [EditorType.TIME]: TimeParameterEditor;
        [EditorType.DATE_TIME]: DateTimeParameterEditor;
        [EditorType.TEXTAREA_PARAMETER_EDITOR]: TextareaParameterEditor;
        [EditorType.JSON_PARAMETER_EDITOR]: JsonParameterEditor;
        [EditorType.SQL_PARAMETER_EDITOR]: SqlParameterEditor;
        [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: SpelTemplateParameterEditor;
        [EditorType.DURATION_EDITOR]: DurationParameterEditor;
        [EditorType.PERIOD_EDITOR]: PeriodParameterEditor;
        [EditorType.CRON_EDITOR]: CronParameterEditor;
        [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: FixedValuesParameterEditor;
    }
>;

export type DualParameterEditor = ParameterEditor & {
    simpleEditor: SimpleEditorProps;
    defaultMode: DualEditorMode;
};

export type EditorProps =
    | TaggedUnion<
          "type",
          {
              [EditorType.RAW_PARAMETER_EDITOR]: RawParameterEditor;
              [EditorType.DUAL_PARAMETER_EDITOR]: DualParameterEditor;
          }
      >
    | SimpleEditorProps;
