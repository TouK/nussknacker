import {TypingResult} from "../../../types";

export function expressionStringToJsonType(expression: String, typ: TypingResult): any {
  const strippedExpression = expression.replace(/^['"]/, "").replace(/['"]$/, "")
  switch (typ.refClazzName) {
    case "java.lang.String": return String(strippedExpression)
    case "java.lang.Long": return Number(expression.replace(/[^-?\d.]/g, ""))
    case "java.lang.Integer": return Number(expression)
    case "java.lang.Double": return Number(expression)
    case "java.lang.Float": return Number(expression)
    case "java.lang.Number": return Number(expression)
    case "java.math.BigDecimal": return Number(expression)
    case "java.math.BigInteger": return Number(expression)
    case "java.lang.Boolean": return Boolean(expression)
    case "java.util.List": return JSON.parse("["+expression.slice(1, -1)+"]")
    default: return expression
  }
}