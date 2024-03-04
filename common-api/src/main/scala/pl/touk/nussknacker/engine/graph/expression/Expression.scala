package pl.touk.nussknacker.engine.graph.expression

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.graph.expression.Expression.Language

// TODO in the future 'expression' should be a dedicated type rather than String, it would for example make DictKeyWithLabelExpression handling prettier
@JsonCodec case class Expression(language: Language, expression: String)

object Expression {

  sealed trait Language

  object Language {
    object Spel                  extends Language
    object SpelTemplate          extends Language
    object DictKeyWithLabel      extends Language
    object TabularDataDefinition extends Language

    implicit val encoder: Encoder[Language] = Encoder.encodeString.contramap {
      case Spel                  => "spel"
      case SpelTemplate          => "spelTemplate"
      case DictKeyWithLabel      => "dictKeyWithLabel"
      case TabularDataDefinition => "tabularDataDefinition"
    }

    implicit val decoder: Decoder[Language] = Decoder.decodeString.emap {
      case "spel"                  => Right(Spel)
      case "spelTemplate"          => Right(SpelTemplate)
      case "dictKeyWithLabel"      => Right(DictKeyWithLabel)
      case "tabularDataDefinition" => Right(TabularDataDefinition)
      case unknown                 => throw new IllegalArgumentException(s"Unknown language [$unknown]")
    }

  }

  def spel(expression: String): Expression = Expression(Language.Spel, expression)

  def spelTemplate(expression: String): Expression = Expression(Language.SpelTemplate, expression)

  def dictKeyWithLabel(key: String, label: Option[String]): Expression = Expression(
    Language.DictKeyWithLabel,
    DictKeyWithLabelExpression(key, label).asJson.noSpaces
  )

  def tabularDataDefinition(definition: String): Expression = Expression(Language.TabularDataDefinition, definition)
}
