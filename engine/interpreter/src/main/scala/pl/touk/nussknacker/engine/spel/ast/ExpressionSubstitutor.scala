package pl.touk.nussknacker.engine.spel.ast

object ExpressionSubstitutor {

  def substitute(expression: String, substitutions: List[ExpressionSubstitution]): String = {
    val sortedDesc = substitutions.sortBy(_.position.start).reverse
    sortedDesc.foldLeft(new StringBuilder(expression)) { (prev, sub) =>
      prev.replace(sub.position.start, sub.position.end, sub.replacement)
    }.toString()
  }

}
