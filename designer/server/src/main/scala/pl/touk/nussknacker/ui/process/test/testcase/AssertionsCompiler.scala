package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.{
  AssertionConfiguredForNotExistingNodesError,
  AssertionExpressionCompilationError
}

import java.util
import scala.jdk.CollectionConverters._

class AssertionsCompiler(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer
) {

  def compile(
      testCase: TestCase,
      scenarioTypingResult: Map[String, NodeTypingData],
      jobData: JobData
  ): ValidatedNel[PerformTestError, CompiledAssertions] = {
    testCase.assertions
      .map { case (node, assertions) =>
        compileNodeAssertions(node, assertions, scenarioTypingResult, jobData).map(node -> _)
      }
      .toList
      .sequence
      .map(assertions => CompiledAssertions(assertions.toMap))
  }

  private def compileNodeAssertions(
      nodeId: NodeId,
      assertions: List[Assertion],
      nodesTyping: Map[String, NodeTypingData],
      jobData: JobData
  ): ValidatedNel[PerformTestError, List[CompiledAssertion]] = {
    validateTypingExistence(nodeId, nodesTyping).andThen { nodeTypingData =>
      val ctx = TestCaseVariables.extendNodeVariablesValidationContext(
        globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData),
        nodeTypingData
      )
      assertions.map(compileAssertionExpression(nodeId, ctx, _)).traverse(_.toValidatedNel)
    }
  }

  private def validateTypingExistence(
      nodeId: NodeId,
      nodesTyping: Map[String, NodeTypingData],
  ): Validated[NonEmptyList[PerformTestError], NodeTypingData] = {
    nodesTyping
      .get(nodeId.id)
      .map(Valid(_))
      .getOrElse(
        Invalid(
          NonEmptyList.one(AssertionConfiguredForNotExistingNodesError(NonEmptyList(nodeId, Nil)))
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
      .leftMap(mapExpressionCompilationErrors(_, assertion, nodeId))
  }

  private def mapExpressionCompilationErrors(
      errors: NonEmptyList[PartSubGraphCompilationError],
      assertion: Assertion,
      nodeId: NodeId
  ) = {
    AssertionExpressionCompilationError(errors, assertion, nodeId)
  }

}

sealed trait AssertionResult

case object SuccessfulAssertion extends AssertionResult

case class FailedAssertion(message: String) extends AssertionResult

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

  // todo: should it work recursively - e.g for arrays nested in lists?
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
      case a: Array[_]           => a.toSeq
      case c: util.Collection[_] => c.asScala.toSeq
    }
  }

  private def produceFailedAssertion(expected: Any, actual: Any) = {
    val expectedStr = SpelValuePrettyPrinter.prettyPrintValue(expected)
    val actualStr   = SpelValuePrettyPrinter.prettyPrintValue(actual)
    FailedAssertion(s"Expected: [$expectedStr] but found [$actualStr]")
  }

}
