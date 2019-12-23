export const parseableBoolean = (expressionObj) => {
  const expression = expressionObj.expression
  const language = expressionObj.language
  return (expression === "true" || expression === "false")
    && language === "spel"
}
