package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParserCompilationError, Mock, TestConfigurationRefersToNotExistingNode}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.Spel
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, FailedAssertion, SuccessfulAssertion}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

class TestCaseCompiler(expressionCompiler: ExpressionCompiler, globalVariablesPreparer: GlobalVariablesPreparer) {

  // todo: to decide where should be input data validation (especially in context of validation during edition and saving)
  def compile(
               testCase: TestCase,
               scenarioTypingResult: Map[String, NodeTypingData],
               jobData: JobData
             ): ValidatedNel[ProcessCompilationError, CompiledTestCase] = {
    val mocksV = testCase.mocks
      .filter(_ => false) // todo: disabled
      .map { case (nodeId, mock) =>
        compileMock(nodeId, mock, scenarioTypingResult, testCase.id).map(nodeId -> _)
      }
      .toList
      .sequence

    val assertionsV = testCase.assertions
      .map { case (node, assertions) =>
        compileAssertions(node, assertions, scenarioTypingResult, testCase.id, jobData).map(node -> _)
      }
      .toList
      .sequence

    ProcessCompilationError.ValidatedNelApplicative.map2(
      mocksV,
      assertionsV
    ) { (validMocks, validAssertions) =>
      CompiledTestCase(
        testCase.id,
        testCase.inputs,
        validMocks.toMap,
        validAssertions.toMap
      )
    }
  }

  private def compileMock(
                           nodeId: NodeId,
                           mock: EnricherMock,
                           nodesTyping: Map[String, NodeTypingData],
                           testId: String
                         ) = {
    validateTypingExistence(nodeId, nodesTyping, testId, Mock).andThen(typing =>
      compileEnricherMockExpression(
        mock.expression,
        typing.outputTyping.getOrElse(throw new IllegalStateException("Output typing for enricher must be provided")),
        ValidationContext(localVariables = typing.variableTypes)
      )(nodeId)
        .map(CompiledEnricherMock(_))
    )
  }

  private def compileEnricherMockExpression(expression: Expression, expectedType: TypingResult, ctx: ValidationContext)(
    implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, CompiledExpression] = {
    expressionCompiler
      .compile(expression, Some(ParameterName("$mockExpression")), ctx, expectedType) match {
      case Valid(typedExpression) =>
        if (typedExpression.typingInfo.typingResult.canBeLooselyAssignedTo(expectedType)) {
          Valid(typedExpression.expression)
        } else {
          Invalid(
            NonEmptyList.one(
              ExpressionParserCompilationError(
                s"Bad expression type, expected: ${expectedType.display}, found: ${typedExpression.typingInfo.typingResult.display}",
                nodeId,
                Some(ParameterName("$mockExpression")),
                expression.expression,
                None
              )
            )
          )
        }
      case invalid@Invalid(_) => invalid
    }
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
        Expression(Spel, assertion.expression),
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
