package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.EmptyMandatoryParameter
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.compile.Validations
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.SubprocessDefinitionExtractor
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression


class NodeCompilerSpec extends AnyFunSuite with Matchers with Inside {
  test("should validate fragment: params definitions with actual params") {
    val subprocessDefinitionExctractor = getSubprocessDefinitionExtractor

    val nodeId = NodeId("fragment1")
    val fragmentDefinitionParams = subprocessDefinitionExctractor.extractBySubprocessId(nodeId.id)
    val fragmentActualParams: List[evaluatedparam.Parameter] = List(
        evaluatedparam.Parameter("P1", Expression("spel", "'ala'")),
        evaluatedparam.Parameter("P2", Expression("spel", "123")),
        evaluatedparam.Parameter("P3", Expression("spel", "'ma'"))
    )

    val validationResult = Validations.validateParametersSafe(fragmentDefinitionParams, fragmentActualParams)(nodeId)
    validationResult shouldBe Valid(())
  }

  test("should validate fragment: non existing params definitions with actual params") {
    val subprocessDefinitionExctractor = getSubprocessDefinitionExtractor

    val nodeId = NodeId("fragment2")
    val fragmentDefinitionParams = subprocessDefinitionExctractor.extractBySubprocessId(nodeId.id)
    val fragmentActualParams: List[evaluatedparam.Parameter] = List(
      evaluatedparam.Parameter("P1", Expression("spel", "'ala'")),
      evaluatedparam.Parameter("P2", Expression("spel", "123")),
      evaluatedparam.Parameter("P3", Expression("spel", "'ma'"))
    )

    val validationResult = Validations.validateParametersSafe(fragmentDefinitionParams, fragmentActualParams)(nodeId)
    validationResult shouldBe Valid(())
  }

  test("should not validate fragment: P2 as mandatory param with missing actual value") {
    val subprocessDefinitionExctractor = getSubprocessDefinitionExtractor
    val nodeId = NodeId("fragment1")
    val fragmentDefinitionParams = subprocessDefinitionExctractor.extractBySubprocessId(nodeId.id)
    val fragmentActualParams: List[evaluatedparam.Parameter] = List(
      evaluatedparam.Parameter("P1", Expression("spel", "'ala'")),
      evaluatedparam.Parameter("P2", Expression("spel", "")),
      evaluatedparam.Parameter("P3", Expression("spel", "'ma'"))
    )
    inside(
      Validations.validateParametersSafe(fragmentDefinitionParams, fragmentActualParams)(nodeId)
    ) {
      case Invalid(nonEmptyList) =>
        inside(nonEmptyList) {
          case NonEmptyList(EmptyMandatoryParameter(_,_,paramName, fragmentId), Nil) =>
            paramName  shouldBe "P2"
            fragmentId shouldBe nodeId.id
        }
    }
  }

  test("should not validate fragment: P3 as mandatory param with default value and missing actual value - ???") {
    val subprocessDefinitionExctractor = getSubprocessDefinitionExtractor
    val nodeId = NodeId("fragment1")
    val fragmentDefinitionParams = subprocessDefinitionExctractor.extractBySubprocessId(nodeId.id)
    val fragmentActualParams: List[evaluatedparam.Parameter] = List(
      evaluatedparam.Parameter("P1", Expression("spel", "'ala'")),
      evaluatedparam.Parameter("P2", Expression("spel", "123")),
      evaluatedparam.Parameter("P3", Expression("spel", ""))
    )
    inside(
      Validations.validateParametersSafe(fragmentDefinitionParams, fragmentActualParams)(nodeId)
    ) {
      case Invalid(nonEmptyList) =>
        inside(nonEmptyList) {
          case NonEmptyList(EmptyMandatoryParameter(_, _, "P3", "fragment1"), Nil) =>
        }
    }
  }

  test("should not validate fragment: P2 and P3 as mandatory param with missing actual values") {
    val subprocessDefinitionExctractor = getSubprocessDefinitionExtractor
    val nodeId = NodeId("fragment1")
    val fragmentDefinitionParams = subprocessDefinitionExctractor.extractBySubprocessId(nodeId.id)
    val fragmentActualParams: List[evaluatedparam.Parameter] = List(
      evaluatedparam.Parameter("P1", Expression("spel", "'ala'")),
      evaluatedparam.Parameter("P2", Expression("spel", "")),
      evaluatedparam.Parameter("P3", Expression("spel", ""))
    )
    inside(
      Validations.validateParametersSafe(fragmentDefinitionParams, fragmentActualParams)(nodeId)
    ) {
      case Invalid(nonEmptyList) =>
        inside(nonEmptyList) {
          case NonEmptyList(EmptyMandatoryParameter(_, _, "P2", "fragment1"), second::Nil) =>
            inside(second){
              case EmptyMandatoryParameter(_, _, "P3", "fragment1") =>
            }
        }
    }
  }

  def getSubprocessDefinitionExtractor = {
    val subprocesses = Map(
      "fragment1" -> ObjectDefinition.withParams(List(
        definition.Parameter[String]("P1").copy(validators = List(), defaultValue = None),
        definition.Parameter[Short]( "P2").copy(validators = List(MandatoryParameterValidator), defaultValue = None),
        definition.Parameter[String]("P3").copy(validators = List(MandatoryParameterValidator), defaultValue = Some("'kuku'"))
      )),
    )
    new SubprocessDefinitionExtractor(category = "RTM", subprocessesDetails = Set.empty, Map.empty, classLoader = this.getClass.getClassLoader) {
      override def extract: Map[String, ObjectDefinition] = subprocesses
    }
  }
}
