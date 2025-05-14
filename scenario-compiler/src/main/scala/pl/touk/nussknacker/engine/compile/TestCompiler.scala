package pl.touk.nussknacker.engine.compile

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compiledgraph.{CompiledTest, CompiledTestSourceInput}
import pl.touk.nussknacker.engine.graph.{Test, TestSourceInput}
import pl.touk.nussknacker.engine.graph.Test.NodeName

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

    sources match {
      case Valid(a)   => Valid(CompiledTest(test.id, a.toMap, Map.empty, Map.empty))
      case Invalid(e) => Invalid(e)
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

}
