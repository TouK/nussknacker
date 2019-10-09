package pl.touk.nussknacker.engine.spel.ast

import org.scalatest.{FunSpec, Matchers}
import org.springframework.expression.spel.SpelNode

class SpelNodePrettyPrinterSpec extends FunSpec with Matchers {

  val parser = new org.springframework.expression.spel.standard.SpelExpressionParser

  it("should print ast without changes") {
    roundTrip("#foo == #bar * 123 + 12")
    roundTrip("#foo == #bar + 123 * 12")
    roundTrip("#foo == (#bar + 123) * 12")
    roundTrip("#foo[123 + 12]")
    roundTrip("#foo.![bar + baz]")
    roundTrip("#foo?.![bar + baz]")
    roundTrip("map.?[value < 27]")
    roundTrip("map?.?[value < 27]")
    roundTrip("#foo--")
    roundTrip("--#foo")
    roundTrip("-#foo")
    roundTrip("PlaceOfBirth?.City")
    roundTrip("foo.bar('baz')")
    roundTrip("foo.getClass == T(int[])")
    roundTrip("'Hello World'?.concat('!')")
  }

  private def roundTrip(givenExpression: String) = {
    val ast = parser.parseRaw(givenExpression).getAST
    val printedExpression = SpelNodePrettyPrinter.pretty(ast)
    withClue(indentedAST(ast)) {
      printedExpression shouldEqual givenExpression
    }
  }

  private def indentedAST(ast: SpelNode): String = {
    val builder = new StringBuilder
    printIndentedAST(builder, ast, "")
    builder.toString()
  }

  // From SpringUtilities
  private def printIndentedAST(sb: StringBuilder, ast: SpelNode, indent: String): Unit = {
    sb.append(indent).append(ast.getClass.getSimpleName)
    sb.append("  value:").append(ast.toStringAST)
    sb.append(if (ast.getChildCount < 2) "" else "  #children:" + ast.getChildCount)
    sb.append("\n")
    0.until(ast.getChildCount).foreach(i => printIndentedAST(sb, ast.getChild(i), indent + "  "))
  }

}
