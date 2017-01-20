package pl.touk.esp.engine.api.deployment

import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.exception.EspExceptionInfo

object test {

  case class TestData(testData: List[String])

  case class TestResults(nodeResults: Map[String, List[NodeResult]] = Map(),
                         invocationResults: Map[String, List[ExpressionInvocationResult]] = Map(),
                         mockedResults: Map[String, List[MockedResult]] = Map(),
                         exceptions: List[EspExceptionInfo[_ <: Throwable]] = List()) {
    def updateResult(nodeId: String, nodeResult: NodeResult) =
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ nodeResult)))

    def updateResult(nodeId: String, invocationResult: ExpressionInvocationResult) =
      copy(invocationResults = invocationResults + (nodeId -> addResults(invocationResult, invocationResults.getOrElse(nodeId, List()))))

    def updateResult(nodeId: String, mockedResult: MockedResult) =
      copy(mockedResults = mockedResults + (nodeId -> (mockedResults.getOrElse(nodeId, List()) :+ mockedResult)))


    //when evaluating e.g. keyBy expression can be invoked more than once...
    //TODO: is it the best way to handle it??
    private def addResults(invocationResult: ExpressionInvocationResult, resultsSoFar: List[ExpressionInvocationResult])
    = resultsSoFar.filterNot(res => res.context.id == invocationResult.context.id && res.name == invocationResult.name) :+ invocationResult

    def updateResult(espExceptionInfo: EspExceptionInfo[_ <: Throwable]) = copy(exceptions = exceptions :+ espExceptionInfo)
  }

  case class NodeResult(context: Context)

  case class ExpressionInvocationResult(context: Context, name: String, result: Any)

  case class MockedResult(context: Context, name: String, result: Any)


}
