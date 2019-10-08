package pl.touk.nussknacker.engine.dict

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import org.springframework.expression.spel.ast.{Indexer, StringLiteral}
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedDict, TypingResult}
import pl.touk.nussknacker.engine.spel.ast

trait DictTyper {

  def typeDictValue(dict: TypedDict, node: Indexer): ValidatedNel[ExpressionParseError, TypingResult]

}

trait DictTyperImpl extends DictTyper {

  import ast.SpelAst._

  override def typeDictValue(dict: TypedDict, node: Indexer): ValidatedNel[ExpressionParseError, TypingResult]  = {
    node.children match {
      case (str: StringLiteral) :: Nil  =>
        val value = str.getLiteralValue.getValue.toString
        val possibleDictValues = possibleValuesFromDict(dict)
        if (possibleDictValues.exists(_ == value))
          Valid(dict.valueType)
        else
          Invalid(ExpressionParseError(s"Illegal value: $value for dict: ${dict.dictId}. Possible values are: ${possibleDictValues}")).toValidatedNel
      case _ =>
        Invalid(ExpressionParseError(s"Illegal spel construction: ${node.toStringAST}. Dict should be indexed by a single key")).toValidatedNel
    }
  }

  protected def possibleValuesFromDict(dict: TypedDict): Iterable[String]

}

object DictTyper {

  val typingDictKeys: DictTyper = new DictTyperImpl {
    override protected def possibleValuesFromDict(dict: TypedDict): Iterable[String] = dict.labelByKey.keys
  }

  val typingDictLabels: DictTyper = new DictTyperImpl {
    override protected def possibleValuesFromDict(dict: TypedDict): Iterable[String] = dict.labelByKey.values
  }

}