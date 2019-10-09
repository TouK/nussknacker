package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast._

/*
  This class basically print AST in the way that it most probable looks like when user was writing it.
  Default implementation of toStringAST adds extra information like round braces for operators or dot before Indexer
  because it is still working SpEL and generating code looks more compact.
 */
object SpelNodePrettyPrinter {

  def pretty(node: SpelNode): String =
    prettyPrint(node)(None)

  private def prettyPrint(node: SpelNode)(implicit parent: Option[SpelNode]): String =
    prettyPrint(node, parent)

  // this version is for overriding parent purpose
  private def prettyPrint(node: SpelNode, parent: Option[SpelNode]): String = {
    implicit val nestedParent: Option[SpelNode] = Some(node)
    node match {
      case e: Assign =>
        prettyPrint(e.getChild(0)) + " = " + prettyPrint(e.getChild(1))
      case e: ConstructorReference =>
        val constructorName = prettyPrint(e.getChild(0))
        1.until(e.getChildCount).map(i => prettyPrint(e.getChild(i))).mkString(s"new $constructorName(", ", ", ")")
      case e: Elvis =>
        prettyPrint(e.getChild(0)) + " ?: " + prettyPrint(e.getChild(1))
      case e: FunctionReference =>
        prettyPrintChildNodes(e).mkString(s"#${SpelNodeHacks.getFunctionName(e)}(", ", ", ")")
      case e: Indexer =>
        prettyPrintChildNodes(e).mkString("[", ", ", "]")
      case e: InlineList =>
        prettyPrintChildNodes(e).mkString("{", ", ", "}")
      case e: InlineMap =>
        prettyPrintChildNodes(e).grouped(2).map(_.mkString(":")).mkString("{", ", ", "}")
      case e: MethodReference =>
        prettyPrintChildNodes(e).mkString(s"${e.getName}(", ", ", ")")
      case e: Projection =>
        "![" + prettyPrint(e.getChild(0)) + "]"
      case e: Ternary =>
        prettyPrint(e.getChild(0)) + " ? " +
          prettyPrint(e.getChild(1)) + " : " +
          prettyPrint(e.getChild(2))
      case e: OperatorNot =>
        "!" + prettyPrint(e.getChild(0))
      case e: Operator =>
        prettyPrintOperator(e, parent)
      case e: CompoundExpression =>
        val children = 0.until(e.getChildCount).map(e.getChild).toList
        val prefixedChildren = children.headOption.map(prettyPrint) ++ children.drop(1).map {
          case c: Indexer => prettyPrint(c) // not prefixed by dot - any other options?
          case c: PropertyOrFieldReference if c.isNullSafe => "?." + prettyPrint(c)
          case c: MethodReference if SpelNodeHacks.isNullSafe(c) => "?." + prettyPrint(c)
          case c: Projection if SpelNodeHacks.isNullSafe(c) => "?." + prettyPrint(c)
          case c: Selection if SpelNodeHacks.isNullSafe(c) => "?." + prettyPrint(c)
          case c => "." + prettyPrint(c)
        }
        prefixedChildren.mkString
      case e: QualifiedIdentifier =>
        val prefix = Option(SpelNodeHacks.getQualifiedId(e)).map(_.getValue.toString).getOrElse("")
        prettyPrintChildNodes(e).mkString(prefix, ".", "")
      case e: Selection =>
        val variant = SpelNodeHacks.getVariant(e)
        val variantString = variant match {
          case Selection.ALL => "?"
          case Selection.FIRST => "^"
          case Selection.LAST => "$"
        }
        variantString + "[" + prettyPrint(e.getChild(0)) + "]"
      case e: TypeReference =>
        0.until(SpelNodeHacks.getDimensions(e)).map(_ => "[]").mkString("T(" + prettyPrint(e.getChild(0)), "", ")")

        // leaf nodes
      case e: VariableReference =>
        e.toStringAST
      case e: PropertyOrFieldReference =>
        e.toStringAST
      case e: BeanReference =>
        e.toStringAST
      case e: Identifier =>
        e.toStringAST
      case e: Literal =>
        e.toStringAST
    }
  }

  private def prettyPrintOperator(e: Operator, parent: Option[SpelNode])(implicit nestedParent: Option[SpelNode]) =
    e match {
      case _: OpDec if SpelNodeHacks.isPostfix(e) =>
        prettyPrint(e.getLeftOperand) + "--"
      case _: OpInc if SpelNodeHacks.isPostfix(e) =>
        prettyPrint(e.getLeftOperand) + "++"
      case _: OpDec =>
        "--" + prettyPrint(e.getLeftOperand)
      case _: OpInc =>
        "++" + prettyPrint(e.getLeftOperand)
      case _: OpMinus =>
        if (e.getChildCount < 2)
          "-" + prettyPrint(e.getLeftOperand)
        else
          prettyPrintGenericOperator(e, parent)
      case _: OpPlus =>
        if (e.getChildCount < 2)
          "+" + prettyPrint(e.getLeftOperand)
        else
          prettyPrintGenericOperator(e, parent)
      case _ =>
        prettyPrintGenericOperator(e, parent)
    }

  private def prettyPrintGenericOperator(e: Operator, parent: Option[SpelNode])(implicit nestedParent: Option[SpelNode]) = {
    val separator = " " + e.getOperatorName + " "
    // avoid round braces as much as possible
    parent match {
      case Some(parentOperator: Operator) if operatorPriority(parentOperator) > operatorPriority(e) =>
        prettyPrintChildNodes(e).mkString("(", separator, ")")
      case _ =>
        prettyPrintChildNodes(e).mkString(separator)
    }
  }

  private def operatorPriority(op: Operator) =
    op match {
      // from https://introcs.cs.princeton.edu/java/11precedence/
      case _: OpDec | _: OpInc => 15
      case _: OpMultiply | _: OpDivide | _: OpModulus => 12
      case _: OpPlus | _: OpMinus => 11
      case _: OpGT | _: OpLT | _: OpGE | _: OpLE | _: OperatorInstanceof => 9
      case _: OpEQ | _: OpNE => 8
      case _: OpAnd => 4
      case _: OpOr => 3

      // not from java
      case _: OperatorBetween => 9 // same as instanceOf?
      case _: OperatorMatches => 9 // same as instanceOf?
      case _: OperatorPower => 9 // same as instanceOf?
    }

  private def prettyPrintChildNodes(e: SpelNode)(implicit nestedParent: Option[SpelNode]) = {
    0.until(e.getChildCount).map(i => prettyPrint(e.getChild(i)))
  }

}
