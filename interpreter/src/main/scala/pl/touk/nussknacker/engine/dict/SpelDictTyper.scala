package pl.touk.nussknacker.engine.dict

import cats.data.Validated.Invalid
import cats.data.{Validated, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast.{Indexer, PropertyOrFieldReference, StringLiteral}
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.dict.DictRegistry.{DictEntryWithKeyNotExists, DictEntryWithLabelNotExists, DictNotDeclared}
import pl.touk.nussknacker.engine.api.generics.SpelParseError
import pl.touk.nussknacker.engine.api.typed.typing.{TypedDict, TypingResult}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.DictError.{DictIndexCountError, DictKeyError, DictLabelError, NoDictError}
import pl.touk.nussknacker.engine.spel.ast

/**
  * It handle typing process off SpEL AST in places where typer referencing to TypedDict type.
  */
trait SpelDictTyper {

  def typeDictValue(dict: TypedDict, node: SpelNode): ValidatedNel[SpelParseError, TypingResult]

}

trait BaseDictTyper extends SpelDictTyper with LazyLogging {

  import ast.SpelAst._

  protected def dictRegistry: DictRegistry

  override def typeDictValue(dict: TypedDict, node: SpelNode): ValidatedNel[SpelParseError, dict.ValueType]  = {
    node match {
      case _: Indexer =>
        node.children match {
          case (str: StringLiteral) :: Nil  =>
            val key = str.getLiteralValue.getValue.toString
            findKey(dict, key)
          case _ =>
            Invalid(DictIndexCountError(node)).toValidatedNel
        }
      case pf: PropertyOrFieldReference  =>
        findKey(dict, pf.getName)
    }
  }

  private def findKey(dict: TypedDict, value: String) = {
    valueForDictKey(dict, value)
      .map(_ => dict.valueType)
      .leftMap {
        case DictNotDeclared(dictId) =>
          // It will happen only if will be used dictionary for which, definition wasn't exposed in ExpressionConfig.dictionaries
          NoDictError(dictId)
        case DictEntryWithLabelNotExists(_, label, possibleLabels) => // FIXME: Use other error type.
          DictLabelError(label, possibleLabels, dict)
        case DictEntryWithKeyNotExists(_, key, possibleKeys) =>
          DictKeyError(key, possibleKeys, dict)
      }.toValidatedNel
  }

  protected def valueForDictKey(dict: TypedDict, key: String): Validated[DictRegistry.DictLookupError, Option[String]]

}

/**
  * This is default dict typer which will be used in most case where we will have process with resolved labels to keys.
  * It will be used in validations during:
  * - import from json
  * - deploy (also on engine's side)
  * - migrations
  */
class KeysDictTyper(protected val dictRegistry: DictRegistry) extends BaseDictTyper {
  override protected def valueForDictKey(dict: TypedDict, key: String): Validated[DictRegistry.DictLookupError, Option[String]] =
    dictRegistry.labelByKey(dict.dictId, key)
}

/**
  * This is typer that will be used just before resolving labels to keys on UI.
  */
class LabelsDictTyper(protected val dictRegistry: DictRegistry) extends BaseDictTyper {
  override protected def valueForDictKey(dict: TypedDict, key: String): Validated[DictRegistry.DictLookupError, Option[String]] =
    dictRegistry.keyByLabel(dict.dictId, key).map(Some(_))
}
