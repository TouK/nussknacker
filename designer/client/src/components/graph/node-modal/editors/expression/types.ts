export enum ExpressionLang {
    SQL = "sql",
    SpEL = "spel",
    SpELTemplate = "spelTemplate",
    String = "string",
    JSON = "json",
    TabularDataDefinition = "tabularDataDefinition",
    DictKeyWithLabel = "dictKeyWithLabel",
    MD = "markdown",
    JsonTemplate = "jsonTemplate",
}

export type ExpressionObj = {
    expression: string;
    language: ExpressionLang | NonNullable<string>;
};

export enum EditorMode {
    SpEL = "spel",
    SpELTemplate = "spelTemplate",
    JsonTemplate = "jsonTemplate",
    SQL = "sql",
}

export enum EditorType {
    SPEL_PARAMETER_EDITOR = "SpelParameterEditor",
    BOOL_PARAMETER_EDITOR = "BoolParameterEditor",
    STATIC_STRING_PARAMETER_EDITOR = "StaticStringParameterEditor",
    FIXED_VALUES_PARAMETER_EDITOR = "FixedValuesParameterEditor",
    MULTI_SELECT_EDITOR = "MultiSelectEditor",
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
    JSON_TEMPLATE_PARAMETER_EDITOR = "JsonTemplateParameterEditor",
}
