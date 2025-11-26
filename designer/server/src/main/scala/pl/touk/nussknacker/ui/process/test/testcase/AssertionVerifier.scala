package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, TestCompiler}
import pl.touk.nussknacker.engine.graph.TestCase
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, ResultContext}

//todo: rename to TestSupport?
trait AssertionVerifier {

  def verify(testCase: TestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]]

}

class NoopAssertionVerifier extends AssertionVerifier {

  override def verify(testCase: TestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]] = {
    Map.empty
  }

}

class AssertionVerifierImpl(modelData: ModelData) extends AssertionVerifier {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper
  private val testCompiler = new TestCompiler(expressionCompiler)

  override def verify(testCase: TestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]] = {
    //compile assertions
    testCompiler

  }

}
