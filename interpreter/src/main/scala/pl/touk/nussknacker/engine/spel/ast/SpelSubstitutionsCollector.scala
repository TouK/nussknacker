package pl.touk.nussknacker.engine.spel.ast

import org.apache.commons.lang3.StringUtils
import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.expression.{ExpressionSubstitution, ExpressionSubstitutionsCollector}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.ast

class SpelSubstitutionsCollector(typeForNode: SpelNode => Option[TypingResult],
                                 replacingStrategy: ReplacingStrategy) extends ExpressionSubstitutionsCollector{


  import ast.SpelAst._

  private lazy val parser = new org.springframework.expression.spel.standard.SpelExpressionParser

  override def collectSubstitutions(expression: Expression): List[ExpressionSubstitution] =
    if(StringUtils.isBlank(expression.expression)) List.empty else collectSubstitutions(expression.expression)

  private[engine] def collectSubstitutions(expression: String): List[ExpressionSubstitution] =
    collectSubstitutions(parser.parseRaw(expression).getAST, Nil, Nil)._2

  private def collectSubstitutions(headNode: SpelNode, typedLowerLevels: List[TypedTreeLevel],
                                   typedSameLevelUntilSelf: List[OptionallyTypedNode]): (OptionallyTypedNode, List[ExpressionSubstitution]) = {
    val typedSelf = OptionallyTypedNode(headNode, typeForNode(headNode))
    val typedTree = TypedTreeLevel(typedSelf :: typedSameLevelUntilSelf) :: typedLowerLevels
    val forSelf = replacingStrategy.findReplacement(typedTree)
      // currently we use getStartPosition and getEndPosition but if we would like to substitute not leaf nodes
      // we should compute range based on children's start/end and based on fact that some tokens are eaten
      // (e.g. ending brace in Indexer) without notice in ast
      .map(ExpressionSubstitution(headNode.positionRange, _))
    val (_, forChildren) = headNode.children.foldLeft((List.empty[OptionallyTypedNode], List.empty[ExpressionSubstitution])) {
      case ((foldedTypedChildren, foldedSubstitutions), childNode) =>
        val (typedChild, subsForChild) = collectSubstitutions(childNode, typedTree, foldedTypedChildren)
        (typedChild :: foldedTypedChildren, subsForChild ::: foldedSubstitutions)
    }
    (typedSelf, forSelf.toList ::: forChildren)
  }

}

trait ReplacingStrategy extends {

  def findReplacement(typedNodeTree: List[TypedTreeLevel]): Option[String]

}

object ReplacingStrategy {

  def fromPartialFunction(pf: PartialFunction[List[TypedTreeLevel], String]): ReplacingStrategy = new ReplacingStrategy {
    override def findReplacement(typedNodeTree: List[TypedTreeLevel]): Option[String] = pf.lift(typedNodeTree)
  }

}

case class TypedTreeLevel(nodes: List[OptionallyTypedNode])

case class OptionallyTypedNode(node: SpelNode, optionalType: Option[TypingResult])
