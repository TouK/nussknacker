export enum ExpressionLang {
    SQL = "sql",
    SpEL = "spel",
    SpELTemplate = "spelTemplate",
    SqlSpELTemplate = "sqlSpelTemplate",
    String = "string",
    JSON = "json",
}

export type ExpressionObj = {
    expression: string;
    language: ExpressionLang | string;
};
