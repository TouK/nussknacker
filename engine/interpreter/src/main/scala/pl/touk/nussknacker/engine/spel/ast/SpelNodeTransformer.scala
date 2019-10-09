package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast._
import SpelNodeTransformer._

class SpelNodeTransformer(pf: PartialFunction[SpelNode, SpelNodeCreateArgs => SpelNodeImpl]) {

  def transform(node: SpelNode): SpelNode =
    // We are using everywhere SpelNodeImpl because it is used in constructors7
    transformInternal(node.asInstanceOf[SpelNodeImpl])

  def transformInternal(node: SpelNodeImpl): SpelNodeImpl = {
    val newChildren = transformChildren(node)
    transformSelf(node, newChildren)
  }

  private def transformChildren(node: SpelNode): Seq[SpelNodeImpl] =
    getChildren(node).map(n => transformInternal(n))

  private def transformSelf(node: SpelNodeImpl, newChildren: Seq[SpelNodeImpl]): SpelNodeImpl =
    pf.lift(node)
      .map(f => f(SpelNodeCreateArgs(getPosition(node), newChildren)))
      .getOrElse(rewriteChildren(node, newChildren))

}

object SpelNodeTransformer {

  private def rewriteChildren(node: SpelNodeImpl, newChildren: Seq[SpelNodeImpl]): SpelNodeImpl = {
    node match {
      case _: Literal | _: BeanReference | _: VariableReference | _: PropertyOrFieldReference | _: Identifier => node // leaf nodes
      case e: Assign => new Assign(getPosition(e), getChildren(e): _*)
      case e: CompoundExpression => new CompoundExpression(getPosition(e), newChildren: _*)
      case e: ConstructorReference => new ConstructorReference(getPosition(e), newChildren: _*)
      case e: Elvis => new Elvis(getPosition(e), newChildren: _*)
      case e: Indexer => new Indexer(getPosition(e), newChildren.head)
      case e: InlineList => new InlineList(getPosition(e), newChildren: _*)
      case e: InlineMap => new InlineMap(getPosition(e), newChildren: _*)
      case e: OpAnd => new OpAnd(getPosition(e), newChildren: _*)
      case e: OpDivide => new OpDivide(getPosition(e), newChildren: _*)
      case e: OpEQ => new OpEQ(getPosition(e), newChildren: _*)
      case e: OpGE => new OpGE(getPosition(e), newChildren: _*)
      case e: OpGT => new OpGT(getPosition(e), newChildren: _*)
      case e: OpLE => new OpLE(getPosition(e), newChildren: _*)
      case e: OpLT => new OpLT(getPosition(e), newChildren: _*)
      case e: OpMinus => new OpMinus(getPosition(e), newChildren: _*)
      case e: OpModulus => new OpModulus(getPosition(e), newChildren: _*)
      case e: OpMultiply => new OpMultiply(getPosition(e), newChildren: _*)
      case e: OpNE => new OpNE(getPosition(e), newChildren: _*)
      case e: OpOr => new OpOr(getPosition(e), newChildren: _*)
      case e: OpPlus => new OpPlus(getPosition(e), newChildren: _*)
      case e: OperatorBetween => new OperatorBetween(getPosition(e), newChildren: _*)
      case e: OperatorInstanceof => new OperatorInstanceof(getPosition(e), newChildren: _*)
      case e: OperatorMatches => new OperatorMatches(getPosition(e), newChildren: _*)
      case e: OperatorNot => new OperatorNot(getPosition(e), newChildren.head)
      case e: OperatorPower => new OperatorPower(getPosition(e), newChildren: _*)
      case e: QualifiedIdentifier => new QualifiedIdentifier(getPosition(e), newChildren: _*)
      case e: Ternary => new Ternary(getPosition(e), newChildren: _*)
      case e: OpDec => new OpDec(getPosition(e), SpelNodeHacks.isPostfix(e), newChildren: _*)
      case e: OpInc => new OpInc(getPosition(e), SpelNodeHacks.isPostfix(e), newChildren: _*)
      case e: TypeReference => new TypeReference(getPosition(e), newChildren.head, SpelNodeHacks.getDimensions(e))
      case e: FunctionReference => new FunctionReference(SpelNodeHacks.getFunctionName(e), getPosition(e), newChildren: _*)
      case e: MethodReference => new MethodReference(SpelNodeHacks.isNullSafe(e), e.getName, getPosition(e), newChildren: _*)
      case e: Projection => new Projection(SpelNodeHacks.isNullSafe(e),  getPosition(e), newChildren.head)
      case e: Selection => new Selection(SpelNodeHacks.isNullSafe(e), SpelNodeHacks.getVariant(e), getPosition(e), newChildren.head)
    }
  }

  private def getPosition(node: SpelNodeImpl): Int =
    SpelNodeHacks.getPosition(node)

  private def getChildren(node: SpelNode): Seq[SpelNodeImpl] =
    0.until(node.getChildCount)
      .map(i => node.getChild(i).asInstanceOf[SpelNodeImpl])

}

case class SpelNodeCreateArgs(position: Int, children: Seq[SpelNodeImpl])