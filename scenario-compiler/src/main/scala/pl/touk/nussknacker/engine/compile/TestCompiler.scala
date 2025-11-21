package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParserCompilationError, InputData, Mock, TestConfigurationRefersToNotExistingNode}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, NodeId, ParamName}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledAssertion, CompiledEnricherMock, CompiledTest, CompiledTestSourceInput}
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.Spel
import pl.touk.nussknacker.engine.graph.{Assertion, EnricherMock, Test, TestSourceInput}
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, FailedAssertion, SuccessfulAssertion}

class TestCompiler(expressionCompiler: ExpressionCompiler) {

  // todo: take care what should be done when scenario is only partially compiled (there were some errors)

  def compile(test: Test, typing: Map[String, NodeTypingInfo]): ValidatedNel[ProcessCompilationError, CompiledTest] = {
    val sources = test.inputs
      .map { case (sourceId, inputDataRecords) =>
        compileInputRecords(NodeId(sourceId), inputDataRecords, typing, test.id).map(sourceId -> _)
      }
      .toList
      .sequence

    val mocks = test.mocks
      .map { case (nodeId, mock) =>
        compileMock(NodeId(nodeId), mock, typing, test.id).map(nodeId -> _)
      }
      .toList
      .sequence

    val assertions = test.assertions
      .map { case (node, assertions) =>
        compileAssertions(NodeId(node), assertions, typing, test.id).map(node -> _)
      }
      .toList
      .sequence

    ProcessCompilationError.ValidatedNelApplicative.map3(
      sources,
      mocks,
      assertions
    ) { (validSources, validMocks, validAssertions) =>
      CompiledTest(
        test.id,
        validSources.toMap,
        validMocks.toMap,
        validAssertions.toMap
      )
    }
  }

  private def compileInputRecords(
                                   nodeId: NodeId,
                                   testSourceInputs: List[TestSourceInput],
                                   nodesTyping: Map[String, NodeTypingInfo],
                                   testId: String
                                 ): ValidatedNel[ProcessCompilationError, List[CompiledTestSourceInput]] = {
    validateTypingExistence(nodeId, nodesTyping, testId, InputData).andThen { _ =>
      testSourceInputs.map(compileInputRecord(nodeId, _)).sequence
    }
  }

  private def compileInputRecord(
                                  nodeId: NodeId,
                                  testSourceInput: TestSourceInput
                                ): ValidatedNel[ProcessCompilationError, CompiledTestSourceInput] = {
    expressionCompiler
      .compile(testSourceInput.expression, None, ValidationContext.empty, Unknown)(nodeId)
      .map(e => CompiledTestSourceInput(e.expression))
  }

  private def compileMock(
                           nodeId: NodeId,
                           mock: EnricherMock,
                           nodesTyping: Map[String, NodeTypingInfo],
                           testId: String
                         ) = {
    validateTypingExistence(nodeId, nodesTyping, testId, Mock).andThen(typing =>
      compileEnricherMockExpression(
        mock.expression,
        typing.outputTyping.getOrElse(throw new IllegalStateException("Output typing for enricher must be provided")),
        typing.inputValidationContext
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
        // todo: this verification probably should be moved to JsonTemplateParser
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
                                 nodesTyping: Map[String, NodeTypingInfo],
                                 testId: String
                               ): ValidatedNel[ProcessCompilationError, List[CompiledAssertion]] = {
    validateTypingExistence(nodeId, nodesTyping, testId, ProcessCompilationError.Assertion).andThen { typing =>
      val ctx = typing.inputValidationContext
        .withVariableUnsafe(
          "results",
          Typed.genericTypeClass(classOf[java.util.List[_]], List(Unknown))
        )
      assertions.map(compileAssertionExpression(nodeId, ctx, _)).sequence
    }
  }

  private def validateTypingExistence(
                                       nodeId: NodeId,
                                       nodesTyping: Map[String, NodeTypingInfo],
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

  @Documentation(description = "Check whether two objects are equals")
  def assertEquals(@ParamName("expected") expected: Any, @ParamName("actual") actual: Any): AssertionResult = {
    if (expected == actual) {
      FailedAssertion(s"Expected: $expected but found $actual")
    }
    SuccessfulAssertion
  }

}
