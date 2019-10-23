package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode
import pl.touk.nussknacker.engine.spel.ast

class SpelNodePrettyPrinter(additionalInformation: SpelNode => String) {

  import ast.SpelAst._

  def print(ast: SpelNode): String = {
    val builder = new StringBuilder
    printIndentedAST(builder, ast, "")
    builder.toString()
  }

  // From SpringUtilities
  private def printIndentedAST(sb: StringBuilder, ast: SpelNode, indent: String): Unit = {
    sb.append(indent).append(ast.getClass.getSimpleName)
    sb.append("  value:").append(ast.toStringAST)
    sb.append("  additional:").append(additionalInformation(ast))
    sb.append(if (ast.getChildCount < 2) "" else "  #children:" + ast.getChildCount)
    sb.append("\n")
    ast.children.foreach(child => printIndentedAST(sb, child, indent + "  "))
  }

}
