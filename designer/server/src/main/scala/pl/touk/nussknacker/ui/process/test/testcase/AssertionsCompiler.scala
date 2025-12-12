package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.TestConfigurationRefersToNotExistingNode
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, FailedAssertion, SuccessfulAssertion}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

import java.util
import scala.jdk.CollectionConverters._

class AssertionsCompiler(expressionCompiler: ExpressionCompiler, globalVariablesPreparer: GlobalVariablesPreparer) {

  def compile(
               testCase: TestCase,
               scenarioTypingResult: Map[String, NodeTypingData],
               jobData: JobData
             ): ValidatedNel[ProcessCompilationError, CompiledAssertions] = {
    testCase.assertions
      .map { case (node, assertions) =>
        compileAssertions(node, assertions, scenarioTypingResult, testCase.name, jobData).map(node -> _)
      }
      .toList
      .sequence
      .map(assertions => CompiledAssertions(assertions.toMap))
  }

  private def compileAssertions(
                                 nodeId: NodeId,
                                 assertions: List[Assertion],
                                 nodesTyping: Map[String, NodeTypingData],
                                 testId: String,
                                 jobData: JobData
                               ): ValidatedNel[ProcessCompilationError, List[CompiledAssertion]] = {
    validateTypingExistence(nodeId, nodesTyping, testId, ProcessCompilationError.Assertion).andThen { typing =>
      val ctx = globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)
        .withVariablesUnsafe(
          "contexts" -> Typed.genericTypeClass(
            classOf[java.util.List[_]],
            List(Typed.record(typing.variableTypes))
          ),
          "TESTS" -> Typed.fromInstance(tests)
        )
      assertions.map(compileAssertionExpression(nodeId, ctx, _)).sequence
    }
  }

  private def validateTypingExistence(
                                       nodeId: NodeId,
                                       nodesTyping: Map[String, NodeTypingData],
                                       testId: String,
                                       contextTestConfigurationPart: ProcessCompilationError.TestConfigurationPart
                                     ) = {
    nodesTyping
      .get(nodeId.id)
      .map(Valid(_))
      .getOrElse(
        Invalid(
          NonEmptyList.one(TestConfigurationRefersToNotExistingNode(nodeId, testId, contextTestConfigurationPart))
        )
      )
  }

  private def compileAssertionExpression(nodeId: NodeId, context: ValidationContext, assertion: Assertion) = {
    expressionCompiler
      .compile(
        assertion.expression,
        None,
        context,
        Typed.typedClass(classOf[AssertionResult])
      )(nodeId)
      .map(e => CompiledAssertion(e.expression))
  }

}

object tests extends TestsFunctions

trait TestsFunctions extends HideToString {

  @Documentation(description = "Check whether two values are equals")
  def assertEquals(@ParamName("expected") expected: Any, @ParamName("actual") actual: Any): AssertionResult = {
    // we use scala "lenient" equals to allow to compare boxed primitives of different types - like 1L and 1
    if (expected == actual) {
      SuccessfulAssertion
    } else if (checkIfSameElements(expected, actual)) {
      SuccessfulAssertion
    } else {
      produceFailedAssertion(expected, actual)
    }
  }

  //todo: should it work recursively - e.g for arrays nested in lists?
  private def checkIfSameElements(expected: Any, actual: Any) = {
    if ((expected.isInstanceOf[Array[_]] || expected.isInstanceOf[util.Collection[_]]) &&
      (actual.isInstanceOf[Array[_]] || actual.isInstanceOf[util.Collection[_]])) {
      convertToSeq(expected) == convertToSeq(actual)
    } else {
      false
    }
  }

  private def convertToSeq(value: Any): Seq[_] = {
    value match {
      case a: Array[_] => a.toSeq
      case c: util.Collection[_] => c.asScala.toSeq
    }
  }

  private def produceFailedAssertion(expected: Any, actual: Any) = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    val actualStr = SpelValuePrettyPrinter.prettyPrintValue(actual)
    FailedAssertion(s"Expected: [$expectedStr] but found [$actualStr]")
  }

}
