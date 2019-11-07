package pl.touk.nussknacker.engine.dict

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{Indexer, PropertyOrFieldReference, StringLiteral}
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{DynamicTypedDict, StaticTypedDict, Typed, TypedDict, TypingResult}
import pl.touk.nussknacker.engine.spel.ast

/**
  * It handle typing process off SpEL AST in places where typer referencing to TypedDict type.
  */
trait SpelDictTyper {

  def typeDictValue(dict: TypedDict, node: SpelNode): ValidatedNel[ExpressionParseError, TypingResult]

}

trait BaseDictTyper extends SpelDictTyper {

  import ast.SpelAst._

  override def typeDictValue(dict: TypedDict, node: SpelNode): ValidatedNel[ExpressionParseError, dict.ValueType]  = {
    node match {
      case _: Indexer =>
        node.children match {
          case (str: StringLiteral) :: Nil  =>
            val key = str.getLiteralValue.getValue.toString
            findKey(dict, key)
          case _ =>
            Invalid(ExpressionParseError(s"Illegal spel construction: ${node.toStringAST}. Dict should be indexed by a single key")).toValidatedNel
        }
      case pf: PropertyOrFieldReference  =>
        findKey(dict, pf.getName)
    }
  }

  private def findKey(dict: TypedDict, key: String) = {
    val possibleDictValues = possibleValuesFromDict(dict)
    if (possibleDictValues.exists(_ == key))
      Valid(dict.valueType)
    else
      Invalid(ExpressionParseError(s"Illegal key: $key for dict: ${dict.dictId}. Possible values are: ${possibleDictValues.mkString(", ")}")).toValidatedNel
  }

  protected def possibleValuesFromDict(dict: TypedDict): Iterable[String]

}

object KeysDictTyper extends BaseDictTyper {
  override protected def possibleValuesFromDict(dict: TypedDict): Iterable[String] = {
    dict match {
      case static: StaticTypedDict => static.labelByKey.keys
      case _: DynamicTypedDict => ??? // FIXME implement
    }
  }
}

object LabelsDictTyper extends BaseDictTyper {
  override protected def possibleValuesFromDict(dict: TypedDict): Iterable[String] = {
    dict match {
      case static: StaticTypedDict => static.labelByKey.values
      case _: DynamicTypedDict => ??? // FIXME implement
    }
  }
}