package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.spel.ast
import pl.touk.nussknacker.engine.spel.ast.SpelAst.PositionRange

class SpelSubstitutionsCollector(typeForNode: SpelNode => Option[TypingResult],
                                 replacementPF: PartialFunction[NodeWithParent, String]) {


  import ast.SpelAst._

  def collectSubstitutions(node: SpelNode): List[ExpressionSubstitution] =
    collectSubstitutions(node, None)

  private def collectSubstitutions(node: SpelNode, parent: Option[OptionallyTypedNode]): List[ExpressionSubstitution] = {
    val typedSelf = OptionallyTypedNode(node, typeForNode(node))
    val forSelf = replacementPF.lift(NodeWithParent(typedSelf, parent))
      // currently we use getStartPosition and getEndPosition but if we would like to substitute not leaf nodes
      // we should compute range based on children's start/end and based on fact that some tokens are eaten
      // (e.g. ending brace in Indexer) without notice in ast
      .map(ExpressionSubstitution(PositionRange(node), _))
    val forChildren = node.children.flatMap(n => collectSubstitutions(n, Some(typedSelf)))
    forSelf.toList ::: forChildren
  }

}

case class NodeWithParent(node: OptionallyTypedNode, parent: Option[OptionallyTypedNode])

case class OptionallyTypedNode(node: SpelNode, optionalType: Option[TypingResult])

case class ExpressionSubstitution(position: PositionRange, replacement: String)