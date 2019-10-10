package pl.touk.nussknacker.engine.spel.ast

import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.spel.ast._

class ExpressionSubstitutorSpec extends FunSuite with Matchers {

  test("indexer substitution") {
    val expression = "#foo['bar']"
    val replacementPF: PartialFunction[OptionallyTypedNode, String] = {
      case OptionallyTypedNode(SpelNodeWithChildren(_: Indexer, (literal: StringLiteral) :: Nil), _) if literal.getLiteralValue.getValue == "bar" =>
        "['baz']"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo['baz']"
  }

  test("property substitution") {
    val expression = "#foo.bar"
    val replacementPF: PartialFunction[OptionallyTypedNode, String] = {
      case OptionallyTypedNode(SpelNodeWithChildren(prop: PropertyOrFieldReference, Nil), _) if prop.getName == "bar" =>
        "baz"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo.baz"
  }

  private def replace(expression: String, replacementPF: PartialFunction[OptionallyTypedNode, String]) = {
    val parser = new org.springframework.expression.spel.standard.SpelExpressionParser
    val ast = parser.parseRaw(expression)
    val collector = new SpelSubstitutionsCollector(_ => None, replacementPF)
    val substitutions = collector.collectSubstitutions(ast.getAST)
    ExpressionSubstitutor.substitute(expression, substitutions)
  }

}
