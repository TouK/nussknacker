package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId}
import pl.touk.nussknacker.engine.testmode.TestProcess.{AssertionResult, FailedAssertion, ResultContext}

import scala.collection.JavaConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}

trait AssertionVerifier {

  def verify(
      testCase: CompiledTestCase,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): Map[NodeId, List[AssertionResult]]

}

class NoopAssertionVerifier extends AssertionVerifier {

  override def verify(
      testCase: CompiledTestCase,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): Map[NodeId, List[AssertionResult]] = {
    Map.empty
  }

}

//todo: differences pretty printer (e.g. rendering arrays as spel arrays not java)
//todo: better equality checking
class AssertionVerifierImpl extends AssertionVerifier {

  override def verify(
      testCase: CompiledTestCase,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): Map[NodeId, List[AssertionResult]] = {
    testCase.assertions.map { case (nodeId, assertions) =>
      nodeId -> assertions.map(assertion => verifySingleAssertions(assertion, nodeId, results))
    }
  }

  private def verifySingleAssertions(
      assertion: CompiledAssertion,
      nodeId: NodeId,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): AssertionResult = {
    // todo: test contextId?
    val contextsForNode = prepareResultsEvaluationContext(nodeId, results)

    val context: Context = Context(ContextId.dummy, Map("contexts" -> contextsForNode))
    try {
      assertion.expression.evaluate[AssertionResult](context, Map("TESTS" -> tests)) match {
        case null                             => FailedAssertion("Assertion result can't be null")
        case assertionResult: AssertionResult => assertionResult
      }
    } catch {
      case e: Exception => FailedAssertion(s"Exception during assertion evaluation: ${e.getMessage}")
    }
  }

  private def prepareResultsEvaluationContext(
      nodeId: NodeId,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): java.util.List[java.util.Map[String, Any]] = {
    results.getOrElse(nodeId, List.empty).map(_.variables.asJava).asJava
  }

}
