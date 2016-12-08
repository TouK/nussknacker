package pl.touk.esp.engine.api.deployment

import pl.touk.esp.engine.api.Context

object test {

  case class TestData(testData: List[String])

  case class TestResults(nodeResults: Map[String, List[NodeResult]] = Map(), invocationResults: Map[String, List[InvocationResult]] = Map()) {
    def updateResult(nodeId: String, nodeResult: NodeResult) =
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ nodeResult)))

    def updateResult(nodeId: String, invocationResult: InvocationResult) =
      copy(invocationResults = invocationResults + (nodeId -> (invocationResults.getOrElse(nodeId, List()) :+ invocationResult)))
  }

  case class NodeResult(context: Context)

  case class InvocationResult(context: Context, params: Map[String, Any])


}
