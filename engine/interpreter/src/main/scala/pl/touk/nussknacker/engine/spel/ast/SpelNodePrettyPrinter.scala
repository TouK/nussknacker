package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.spel.SpelNode

class SpelNodePrettyPrinter(additionalInformation: SpelNode => String) {

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
    0.until(ast.getChildCount).foreach(i => printIndentedAST(sb, ast.getChild(i), indent + "  "))
  }

}
