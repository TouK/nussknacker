package pl.touk.nussknacker.engine.expression

object ExpressionSubstitutor {

  def substitute(expression: String, substitutions: List[ExpressionSubstitution]): String = {
    if (substitutions.isEmpty) {
      expression
    } else {
      val sortedDesc = substitutions.sortBy(_.textRange.start).reverse
      sortedDesc
        .foldLeft(new StringBuilder(expression)) { (prev, sub) =>
          prev.replace(sub.textRange.start, sub.textRange.end, sub.replacement)
        }
        .toString()
    }
  }

}
