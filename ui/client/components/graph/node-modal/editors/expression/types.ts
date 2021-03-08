export enum ExpressionLang {
  SQL = "sql",
  SpEL = "spel",
  String = "string"
}

export type ExpressionObj = {
  expression: string,
  language: ExpressionLang | string,
}
