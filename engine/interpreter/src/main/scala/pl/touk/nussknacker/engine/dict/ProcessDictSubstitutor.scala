package pl.touk.nussknacker.engine.dict

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.spel.ast.{Indexer, PropertyOrFieldReference, StringLiteral}
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.dict.DictRegistry.{DictEntryWithKeyNotExists, DictLookupError}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.TypedDict
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessNodesRewriter}
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor.KeyToLabelReplacingStrategy
import pl.touk.nussknacker.engine.expression.{ExpressionSubstitutionsCollector, ExpressionSubstitutor}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.{OptionallyTypedNode, ReplacingStrategy, SpelSubstitutionsCollector, TypedTreeLevel}

class ProcessDictSubstitutor(dictRegistry: DictRegistry,
                             replacingStrategy: ReplacingStrategy,
                             prepareSubstitutionsCollector: (ExpressionTypingInfo, ReplacingStrategy) => Option[ExpressionSubstitutionsCollector]) extends LazyLogging {

  def substitute(process: CanonicalProcess, processTypingInfo: Map[String, Map[String, ExpressionTypingInfo]]): CanonicalProcess = {
    val rewriter = ProcessNodesRewriter.rewritingAllExpressions { exprIdWithMetadata =>expr =>
      val nodeExpressionId = exprIdWithMetadata.expressionId
      val nodeTypingInfo = processTypingInfo.getOrElse(nodeExpressionId.nodeId.id, Map.empty)
      val optionalExpressionTypingInfo = nodeTypingInfo.get(nodeExpressionId.expressionId)
      val substitutedExpression = optionalExpressionTypingInfo.flatMap(prepareSubstitutionsCollector(_, replacingStrategy)).map { substitutionsCollector =>
        val substitutions = substitutionsCollector.collectSubstitutions(expr)
        val afterSubstitution = ExpressionSubstitutor.substitute(expr.expression, substitutions)
        if (substitutions.nonEmpty)
          logger.debug(s"Found ${substitutions.size} substitutions in expression: ${process.metaData.id} > ${nodeExpressionId.nodeId.id} > ${nodeExpressionId.expressionId}. " +
            s"Expression: '${expr.expression}' replaced with '$afterSubstitution'")
        afterSubstitution
      }.getOrElse(expr.expression)
      expr.copy(expression = substitutedExpression)
    }

    rewriter.rewriteProcess(process)
  }

  def reversed: ProcessDictSubstitutor = new ProcessDictSubstitutor(dictRegistry, new KeyToLabelReplacingStrategy(dictRegistry), prepareSubstitutionsCollector)

}

object ProcessDictSubstitutor extends LazyLogging {

  def apply(dictRegistry: DictRegistry): ProcessDictSubstitutor = {
    new ProcessDictSubstitutor(dictRegistry, new LabelToKeyReplacingStrategy(dictRegistry), prepareSubstitutionsCollector)
  }

  // TODO: add ExpressionSubstitutionsCollector "type class" for ExpressionTypingInfo so it will be possible to add new ExpressionParser without changing this class...
  private def prepareSubstitutionsCollector(typingInfo: ExpressionTypingInfo, replacingStrategy: ReplacingStrategy) = typingInfo match {
    case SpelExpressionTypingInfo(intermediateResults, _) =>
      Some(new SpelSubstitutionsCollector(n => intermediateResults.get(SpelNodeId(n)), replacingStrategy))
    case _ =>
      None
  }

  trait BaseReplacingStrategy extends ReplacingStrategy {

    protected def dictRegistry: DictRegistry

    def findReplacement(typedNodeTree: List[TypedTreeLevel]): Option[String] = typedNodeTree match {
      case
        TypedTreeLevel(OptionallyTypedNode(indexerKey: StringLiteral, _) :: Nil) ::
          TypedTreeLevel(OptionallyTypedNode(_: Indexer, _) :: OptionallyTypedNode(_, Some(dict: TypedDict)) :: _) :: _ =>
        val indexerKeyValue = indexerKey.getLiteralValue.getValue.toString
        val replacement = findDictReplacement(dict, indexerKeyValue)

        replacement.map(key => s"'$key'")
      case
        TypedTreeLevel(OptionallyTypedNode(property: PropertyOrFieldReference, _) :: OptionallyTypedNode(_, Some(dict: TypedDict)) :: Nil) :: _ =>
        val propertyName = property.getName
        findDictReplacement(dict, propertyName)
      case _ => None
    }

    private def findDictReplacement(dict: TypedDict, value: String): Option[String] = {
      dictLookup(dict, value) match {
        case Invalid(DictEntryWithKeyNotExists(_, key, possibleKeys)) =>
          logger.warn(s"Can't find label for key: $key in ${dict.display}, possible keys: ${possibleKeys}. Probable change in dict definition. Will be used key in this place.")
          None
        case Invalid(err) => // shouldn't happen
          logger.error(s"Unexpected error: $err. Should be handled in typing phase.")
          None
        case Valid(value) =>
          value
      }
    }

    protected def dictLookup(dict: TypedDict, value: String): Validated[DictLookupError, Option[String]]

  }

  /**
    * This is default ReplacingStrategy which will be used before saving process, when we need to resolve labels to keys.
    */
  class LabelToKeyReplacingStrategy(protected val dictRegistry: DictRegistry) extends BaseReplacingStrategy {
    override protected def dictLookup(dict: TypedDict, value: String): Validated[DictLookupError, Option[String]] =
      dictRegistry.keyByLabel(dict.dictId, value).map(Some(_))
  }

  /**
    * This "reversed" ReplacingStrategy which will be used for presentation of resolved expressions. It must be not restrictive
    * for case when label is missing for given key, because possible values for dictionary can change and we need to
    * give users ability to replace invalid keys with correct once.
    */
  class KeyToLabelReplacingStrategy(protected val dictRegistry: DictRegistry) extends BaseReplacingStrategy {
    override protected def dictLookup(dict: TypedDict, value: String): Validated[DictLookupError, Option[String]] =
      dictRegistry.labelByKey(dict.dictId, value)
  }

}
