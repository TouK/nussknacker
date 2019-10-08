package pl.touk.nussknacker.engine.spel.ast

import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.spel.ast._

class ExpressionSubstitutorSpec extends FunSuite with Matchers {

  test("indexer substitution") {
    val expression = "#foo['bar']"
    val replacementPF: PartialFunction[NodeWithParent, String] = {
      case NodeWithParent(
      OptionallyTypedNode(literal: StringLiteral, _),
      Some(OptionallyTypedNode(_: Indexer, _))) if literal.getLiteralValue.getValue == "bar" =>
        "'baz'"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo['baz']"
  }

  test("property substitution") {
    val expression = "#foo.bar"
    val replacementPF: PartialFunction[NodeWithParent, String] = {
      case NodeWithParent(OptionallyTypedNode(prop: PropertyOrFieldReference, _), _) if prop.getName == "bar" =>
        "baz"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo.baz"
  }

  test("multiple substitutions") {
    val expression = "#foo.bar != #foo.baz"
    val replacementPF: PartialFunction[NodeWithParent, String] = {
      case NodeWithParent(OptionallyTypedNode(prop: PropertyOrFieldReference, _), _) =>
        prop.getName + prop.getName
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo.barbar != #foo.bazbaz"
  }

  private def replace(expression: String, replacementPF: PartialFunction[NodeWithParent, String]) = {
    val parser = new org.springframework.expression.spel.standard.SpelExpressionParser
    val ast = parser.parseRaw(expression)
    val collector = new SpelSubstitutionsCollector(_ => None, replacementPF)
    val substitutions = collector.collectSubstitutions(ast.getAST)
    ExpressionSubstitutor.substitute(expression, substitutions)
  }

}
