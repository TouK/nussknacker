package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.compiledgraph.CompiledTestCase
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, ResultContext}

trait AssertionVerifier {

  def verify(testCase: CompiledTestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]]

}

class NoopAssertionVerifier extends AssertionVerifier {

  override def verify(testCase: CompiledTestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]] = {
    Map.empty
  }

}

class AssertionVerifierImpl extends AssertionVerifier {

  override def verify(testCase: CompiledTestCase, results: Map[NodeId, List[ResultContext[Any]]]): Map[NodeId, List[AssertionResult]] = {
    ???
  }

}
