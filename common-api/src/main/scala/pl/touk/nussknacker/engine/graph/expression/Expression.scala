package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec

@JsonCodec case class Expression(language: String, expression: String)

object Expression {
  object Language {
    val Spel = "spel"
    val SpelTemplate = "spelTemplate"
  }

  def spel(expression: String): Expression = Expression(Language.Spel, expression)

  def spelTemplate(expression: String): Expression = Expression(Language.SpelTemplate, expression)

}