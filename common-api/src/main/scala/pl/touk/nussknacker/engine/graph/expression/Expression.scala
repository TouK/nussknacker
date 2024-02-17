package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps

// TODO in the future 'expression' should be a dedicated type rather than String, it would for example make DictKeyWithLabelExpression handling prettier
@JsonCodec case class Expression(language: String, expression: String)

object Expression {

  object Language {
    val Spel             = "spel"
    val SpelTemplate     = "spelTemplate"
    val DictKeyWithLabel = "dictKeyWithLabel"
    val TabularDataDefinition = "tabularDataDefinition"
  }

  def spel(expression: String): Expression = Expression(Language.Spel, expression)

  def spelTemplate(expression: String): Expression = Expression(Language.SpelTemplate, expression)

  @JsonCodec
  case class DictKeyWithLabelExpression(
      key: String,
      label: Option[
        String
      ] // label is optional as there is no reason to keep in on BE side, it's resolved for FE in ProcessDictSubstitutor
  )

  def dictKeyWithLabel(key: String, label: Option[String]): Expression = Expression(
    Language.DictKeyWithLabel,
    DictKeyWithLabelExpression(key, label).asJson.noSpaces
  )

  def tabularDataDefinition(expression: String): Expression = Expression(Language.TabularDataDefinition, expression)
}
