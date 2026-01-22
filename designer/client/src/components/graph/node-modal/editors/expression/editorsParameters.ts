import { EditorType, ExpressionLang } from "./types";

export const editorsParameters: Record<EditorType, { displayName: string; language: ExpressionLang }> = {
    [EditorType.BOOL_PARAMETER_EDITOR]: { displayName: "Boolean", language: ExpressionLang.SpEL },
    [EditorType.CRON_EDITOR]: { displayName: "Cron", language: ExpressionLang.SpEL },
    [EditorType.DATE]: { displayName: "Date", language: ExpressionLang.SpEL },
    [EditorType.DATE_TIME]: { displayName: "Datetime", language: ExpressionLang.SpEL },
    [EditorType.DURATION_EDITOR]: { displayName: "Duration", language: ExpressionLang.SpEL },
    [EditorType.FIXED_VALUES_PARAMETER_EDITOR]: {
        displayName: "Fixed Values",
        language: ExpressionLang.SpEL,
    },
    [EditorType.MULTI_SELECT_EDITOR]: {
        displayName: "Multiple Fixed Values",
        language: ExpressionLang.JSON,
    },
    [EditorType.FIXED_VALUES_WITH_ICON_PARAMETER_EDITOR]: {
        displayName: "Fixed Values",
        language: ExpressionLang.SpEL,
    },
    [EditorType.FIXED_VALUES_WITH_RADIO_PARAMETER_EDITOR]: {
        displayName: "Fixed Values",
        language: ExpressionLang.SpEL,
    },
    [EditorType.JSON_PARAMETER_EDITOR]: { displayName: "Json", language: ExpressionLang.JSON },
    [EditorType.PERIOD_EDITOR]: { displayName: "Period", language: ExpressionLang.SpEL },
    [EditorType.SPEL_PARAMETER_EDITOR]: { displayName: "Expression", language: ExpressionLang.SpEL },
    [EditorType.STATIC_STRING_PARAMETER_EDITOR]: { displayName: "Text", language: ExpressionLang.String },
    [EditorType.TEXTAREA_PARAMETER_EDITOR]: { displayName: "Text", language: ExpressionLang.SpEL },
    [EditorType.TIME]: { displayName: "Time", language: ExpressionLang.SpEL },
    [EditorType.SQL_PARAMETER_EDITOR]: { displayName: "SQL", language: ExpressionLang.SpELTemplate },
    [EditorType.SPEL_TEMPLATE_PARAMETER_EDITOR]: {
        displayName: "String Template",
        language: ExpressionLang.SpELTemplate,
    },
    [EditorType.DICT_PARAMETER_EDITOR]: {
        displayName: "Dictionary",
        language: ExpressionLang.DictKeyWithLabel,
    },
    [EditorType.TABLE_EDITOR]: { displayName: "Table", language: ExpressionLang.TabularDataDefinition },
    [EditorType.JSON_TEMPLATE_PARAMETER_EDITOR]: {
        displayName: "Json Template",
        language: ExpressionLang.JsonTemplate,
    },
    [EditorType.NAME_VALUE_LIST_EDITOR]: {
        displayName: "list",
        language: ExpressionLang.SpEL,
    },
};
