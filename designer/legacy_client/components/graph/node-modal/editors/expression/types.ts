export enum ExpressionLang {
  SQL = "sql",
  SpEL = "spel",
  String = "string",
  JSON = "json"
}

export type ExpressionObj = {
  expression: string,
  language: ExpressionLang | string,
}
