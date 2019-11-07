package pl.touk.nussknacker.engine.dict

import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.spel.ast.{Indexer, StringLiteral}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.StaticTypedDict
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, ProcessRewriter}
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor.KeyToLabelReplacingStrategy
import pl.touk.nussknacker.engine.expression.{ExpressionSubstitutionsCollector, ExpressionSubstitutor}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.{OptionallyTypedNode, ReplacingStrategy, SpelSubstitutionsCollector, TypedTreeLevel}

class ProcessDictSubstitutor(replacingStrategy: ReplacingStrategy,
                             prepareSubstitutionsCollector: (ExpressionTypingInfo, ReplacingStrategy) => Option[ExpressionSubstitutionsCollector]) extends LazyLogging {

  def substitute(process: CanonicalProcess, processTypingInfo: Map[String, Map[String, ExpressionTypingInfo]]): CanonicalProcess = {
    val rewriter = ProcessRewriter.rewritingAllExpressions { exprIdWithMetadata => expr =>
      val nodeExpressionId = exprIdWithMetadata.expressionId
      val nodeTypingInfo = processTypingInfo.getOrElse(nodeExpressionId.nodeId.id, Map.empty)
      val optionalExpressionTypingInfo = nodeTypingInfo.get(nodeExpressionId.expressionId)
      val substitutedExpression = optionalExpressionTypingInfo.flatMap(prepareSubstitutionsCollector(_, replacingStrategy)).map { substitutionsCollector =>
        val substitutions = substitutionsCollector.collectSubstitutions(expr)
        val afterSubstitution = ExpressionSubstitutor.substitute(expr.expression, substitutions)
        if (substitutions.nonEmpty)
          logger.debug(s"Found ${substitutions.size} substitutions in expression: ${process.metaData.id}>${nodeExpressionId.nodeId.id}>${nodeExpressionId.expressionId}. " +
            s"Expression: '${expr.expression}' replaced with '$afterSubstitution'")
        afterSubstitution
      }.getOrElse(expr.expression)
      expr.copy(expression = substitutedExpression)
    }

    rewriter.rewriteProcess(process)
  }

  def reversed: ProcessDictSubstitutor = new ProcessDictSubstitutor(KeyToLabelReplacingStrategy, prepareSubstitutionsCollector)

}

object ProcessDictSubstitutor extends LazyLogging {

  def apply(): ProcessDictSubstitutor = {
    new ProcessDictSubstitutor(LabelToKeyReplacingStrategy, prepareSubstitutionsCollector)
  }

  private def prepareSubstitutionsCollector(typingInfo: ExpressionTypingInfo, replacingStrategy: ReplacingStrategy) = typingInfo match {
    case SpelExpressionTypingInfo(intermediateResults) =>
      Some(new SpelSubstitutionsCollector(n => intermediateResults.get(SpelNodeId(n)), replacingStrategy))
    case _ =>
      None
  }

  trait BaseReplacingStrategy extends ReplacingStrategy {

    def findReplacement(typedNodeTree: List[TypedTreeLevel]): Option[String] = typedNodeTree match {
      case
        TypedTreeLevel(OptionallyTypedNode(indexerKey: StringLiteral, _) :: Nil) ::
          TypedTreeLevel(OptionallyTypedNode(_: Indexer, _) :: OptionallyTypedNode(_, Some(static: StaticTypedDict)) :: _) :: _ =>
        val indexerKeyValue = indexerKey.getLiteralValue.getValue.toString
        val replacement = findStaticDictReplacement(static, indexerKeyValue)

        replacement.map(key => s"'$key'")
      case _ => None
    }

    protected def findStaticDictReplacement(static: StaticTypedDict, indexerKeyValue: String): Option[String]

  }

  object LabelToKeyReplacingStrategy extends BaseReplacingStrategy {

    override protected def findStaticDictReplacement(static: StaticTypedDict, indexerKeyValue: String): Option[String] = {
      val replacement = static.keysByLabel.get(indexerKeyValue)
      if (replacement.isEmpty) {
        logger.warn(s"Can't find key for label: $indexerKeyValue in dict: $static. Probable change in dict definition. Will be used label in this place.")
      }
      replacement
    }

  }

  object KeyToLabelReplacingStrategy extends BaseReplacingStrategy {

    override protected def findStaticDictReplacement(static: StaticTypedDict, indexerKeyValue: String): Option[String] = {
      val replacement = static.labelByKey.get(indexerKeyValue)
      if (replacement.isEmpty) {
        logger.warn(s"Can't find label for key: $indexerKeyValue in dict: $static. Probable change in dict definition. Will be used key in this place.")
      }
      replacement
    }

  }

}
