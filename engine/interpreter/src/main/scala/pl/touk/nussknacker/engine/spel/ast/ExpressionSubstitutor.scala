package pl.touk.nussknacker.engine.spel.ast

object ExpressionSubstitutor {

  def substitute(expression: String, substitutions: List[ExpressionSubstitution]): String = {
    val sortedDesc = substitutions.sortBy(_.startPosition).reverse
    sortedDesc.foldLeft(new StringBuilder(expression)) { (prev, sub) =>
      prev.replace(sub.startPosition, sub.endPosition + 1, sub.replacement)
    }.toString()
  }

}
