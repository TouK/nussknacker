package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps

// TODO in the future 'expression' should be a dedicated type rather than raw string, it would for example make DictLabelWithKeyExpression handling prettier
@JsonCodec case class Expression(language: String, expression: String)

object Expression {

  object Language {
    val Spel             = "spel"
    val SpelTemplate     = "spelTemplate"
    val Literal          = "literal"
    val DictLabelWithKey = "dictLabelWithKey"
  }

  def spel(expression: String): Expression = Expression(Language.Spel, expression)

  def spelTemplate(expression: String): Expression = Expression(Language.SpelTemplate, expression)

  def literal(expression: String): Expression = Expression(Language.Literal, expression)

  @JsonCodec
  case class DictLabelWithKeyExpression(dictId: String, label: String, key: String)

  def dictLabelWithKey(dictId: String, label: String, key: String): Expression = Expression(
    Language.DictLabelWithKey,
    DictLabelWithKeyExpression(dictId, label, key).asJson.noSpaces
  )

}
