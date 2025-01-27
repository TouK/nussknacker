package pl.touk.nussknacker.engine.graph.expression

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.{
  DictKeyWithLabel,
  Spel,
  SpelTemplate,
  TabularDataDefinition
}

// TODO in the future 'expression' should be a dedicated type rather than String, it would for example make DictKeyWithLabelExpression handling prettier
@JsonCodec case class Expression(language: Language, expression: String)

object Expression {

  sealed trait Language extends Serializable {

    override def toString: String = this match {
      case Spel                  => "spel"
      case SpelTemplate          => "spelTemplate"
      case DictKeyWithLabel      => "dictKeyWithLabel"
      case TabularDataDefinition => "tabularDataDefinition"
    }

  }

  object Language {
    object Spel                  extends Language
    object SpelTemplate          extends Language
    object DictKeyWithLabel      extends Language
    object TabularDataDefinition extends Language

    implicit val encoder: Encoder[Language] = Encoder.encodeString.contramap(_.toString)

    implicit val decoder: Decoder[Language] = Decoder.decodeString.emap {
      case "spel"                  => Right(Spel)
      case "spelTemplate"          => Right(SpelTemplate)
      case "dictKeyWithLabel"      => Right(DictKeyWithLabel)
      case "tabularDataDefinition" => Right(TabularDataDefinition)
      case unknown                 => Left(s"Unknown language [$unknown]")
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
