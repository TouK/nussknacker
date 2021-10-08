package pl.touk.nussknacker.engine.expression

import org.scalatest.{FunSuite, Matchers}
import org.springframework.expression.spel.ast._
import pl.touk.nussknacker.engine.spel.ast.{OptionallyTypedNode, ReplacingStrategy, SpelSubstitutionsCollector, TypedTreeLevel}

class ExpressionSubstitutorSpec extends FunSuite with Matchers {

  test("indexer substitution") {
    val expression = "#foo['bar']"
    val replacementPF: PartialFunction[List[TypedTreeLevel], String] = {
      case
        TypedTreeLevel(OptionallyTypedNode(literal: StringLiteral, _) :: Nil) ::
        TypedTreeLevel(OptionallyTypedNode(_: Indexer, _) :: _) ::
        _ if literal.getLiteralValue.getValue == "bar" =>
        "'baz'"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo['baz']"
  }

  test("property substitution") {
    val expression = "#foo.bar"
    val replacementPF: PartialFunction[List[TypedTreeLevel], String] = {
      case TypedTreeLevel(OptionallyTypedNode(prop: PropertyOrFieldReference, _) :: _) :: _ if prop.getName == "bar" =>
        "baz"
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo.baz"
  }

  test("multiple substitutions") {
    val expression = "#foo.bar != #foo.baz"
    val replacementPF: PartialFunction[List[TypedTreeLevel], String] = {
      case TypedTreeLevel(OptionallyTypedNode(prop: PropertyOrFieldReference, _) :: _) :: _ =>
        prop.getName + prop.getName
    }

    val result = replace(expression, replacementPF)

    result shouldEqual "#foo.barbar != #foo.bazbaz"
  }

  private def replace(expression: String, replacementPF: PartialFunction[List[TypedTreeLevel], String]) = {
    val collector = new SpelSubstitutionsCollector(_ => None, ReplacingStrategy.fromPartialFunction(replacementPF))
    val substitutions = collector.collectSubstitutions(expression)
    ExpressionSubstitutor.substitute(expression, substitutions)
  }

}
