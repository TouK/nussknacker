package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api.{Documentation, HideToString, NodeId, ParamName}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledAssertion, CompiledTest, CompiledTestSourceInput}
import pl.touk.nussknacker.engine.graph.{Assertion, Test, TestSourceInput}
import pl.touk.nussknacker.engine.graph.Test.NodeName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.Spel

import scala.collection.immutable

class TestCompiler(expressionCompiler: ExpressionCompiler) {

  def compile(test: Test, typing: Map[String, NodeTypingInfo]): ValidatedNel[ProcessCompilationError, CompiledTest] = {
    val inputCompilationResults: immutable.Iterable[
      Validated[NonEmptyList[ProcessCompilationError], (NodeName, List[CompiledTestSourceInput])]
    ] = for {
      (sourceId, inputDataRecords) <- test.inputs
    } yield compileInputRecords(NodeId(sourceId), inputDataRecords).map(sourceId -> _)
    val sources: Validated[NonEmptyList[ProcessCompilationError], List[(NodeName, List[CompiledTestSourceInput])]] =
      inputCompilationResults.toList.sequence

    val assertionCompilationResults
        : Validated[NonEmptyList[ProcessCompilationError], List[(NodeName, List[CompiledAssertion])]] = {
      for {
        (node, assertions) <- test.assertions
      } yield compileAssertions(NodeId(node), assertions, typing(node)).map(node -> _)
    }.toList.sequence

    ProcessCompilationError.ValidatedNelApplicative.map2( // todo: ensure that errors are cumulated
      sources,
      assertionCompilationResults
    ) { (validSources, validAssertions) =>
      CompiledTest(test.id, validSources.toMap, Map.empty, validAssertions.toMap)
    }
  }

  private def compileInputRecords(
      nodeId: NodeId,
      testSourceInputs: List[TestSourceInput]
  ): ValidatedNel[ProcessCompilationError, List[CompiledTestSourceInput]] = {
    testSourceInputs.map(compileInputRecord(nodeId, _)).sequence
  }

  private def compileInputRecord(
      nodeId: NodeId,
      testSourceInput: TestSourceInput
  ): ValidatedNel[ProcessCompilationError, CompiledTestSourceInput] = {
    expressionCompiler
      .compile(testSourceInput.expression, None, ValidationContext.empty, Unknown)(nodeId)
      .map(e => CompiledTestSourceInput(e.expression))
  }

  private def compileAssertions(
      nodeId: NodeId,
      assertions: List[Assertion],
      nodeTyping: NodeTypingInfo
  ): ValidatedNel[ProcessCompilationError, List[CompiledAssertion]] = {
    val context = nodeTyping.inputValidationContext
      .withVariableUnsafe(
        "results",
        Typed.genericTypeClass(classOf[java.util.List[_]], List(Unknown))
      ) // todo: better typing
    assertions.map { assertion =>
      val assertionValidationContext = context
      expressionCompiler
        .compile(
          Expression(Spel, assertion.expression),
          None,
          assertionValidationContext,
          Typed.typedClass(classOf[AssertionResult])
        )(nodeId)
        .map(e => CompiledAssertion(e.expression))
    }.sequence
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

sealed trait AssertionResult

object SuccessfulAssertion extends AssertionResult

//todo: mby message can be easily hidden
case class FailedAssertion(message: String) extends AssertionResult
