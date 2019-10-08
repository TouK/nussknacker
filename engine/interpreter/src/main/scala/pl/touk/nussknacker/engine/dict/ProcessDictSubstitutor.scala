package pl.touk.nussknacker.engine.dict

import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.spel.ast.{Indexer, StringLiteral}
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.StaticTypedDict
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.NodeTypingInfo
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor.KeyToLabelReplacingStrategy
import pl.touk.nussknacker.engine.expression.{ExpressionSubstitutionsCollector, ExpressionSubstitutor}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{NodeData, Sink}
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.spel.ast.SpelAst.SpelNodeId
import pl.touk.nussknacker.engine.spel.ast.{OptionallyTypedNode, ReplacingStrategy, SpelSubstitutionsCollector, TypedTreeLevel}

class ProcessDictSubstitutor(replacingStrategy: ReplacingStrategy,
                             prepareSubstitutionsCollector: (ExpressionTypingInfo, ReplacingStrategy) => Option[ExpressionSubstitutionsCollector]) extends LazyLogging {

  def substitute(process: CanonicalProcess, processTypingInfo: Map[String, Map[String, ExpressionTypingInfo]]): CanonicalProcess = {
    def substitute(node: CanonicalNode): CanonicalNode = {
      val nodeTypingInfo = processTypingInfo.getOrElse(node.id, Map.empty)

      def substituteExpression(expressionId: String, expr: Expression) = {
        val substitutedExpression = nodeTypingInfo.get(expressionId).flatMap(prepareSubstitutionsCollector(_, replacingStrategy)).map { substitutionsCollector =>
          val substitutions = substitutionsCollector.collectSubstitutions(expr)
          val afterSubstitution = ExpressionSubstitutor.substitute(expr.expression, substitutions)
          if (substitutions.nonEmpty)
            logger.debug(s"Found ${substitutions.size} substitutions in expression: ${process.metaData.id}>${node.id}>$expressionId. " +
              s"Expression: '${expr.expression}' replaced with '$afterSubstitution'")
          afterSubstitution
        }.getOrElse(expr.expression)
        expr.copy(expression = substitutedExpression)
      }

      def substituteNode(data: NodeData) = {
        data match {
          // FIXME other node data and expressions
          case sink@Sink(_, _, Some(endResultExpression), _, _) => sink.copy(endResult = Some(substituteExpression(NodeTypingInfo.DefaultExpressionId, endResultExpression)))
          case _ => data
        }
      }
      node match {
        case flat: FlatNode =>
          flat.copy(data = substituteNode(flat.data))
        // FIXME other nodes
        case _ => node
      }
    }

    // FIXME exception handler
    process.copy(nodes = process.nodes.map(substitute))
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
