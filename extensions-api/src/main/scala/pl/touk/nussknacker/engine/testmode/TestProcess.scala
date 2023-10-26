package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, ContextId}

object TestProcess {

  case class TestResults(
      nodeResults: Map[String, List[NodeResult]],
      invocationResults: Map[String, List[ExpressionInvocationResult]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult]],
      exceptions: List[NuExceptionInfo[_ <: Throwable]]
  ) {

    def updateNodeResult(nodeId: String, context: Context): TestResults = {
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ NodeResult(context))))
    }

    def updateExpressionResult(nodeId: String, context: Context, name: String, result: Any): TestResults = {
      val invocationResult = ExpressionInvocationResult(context.id, name, result)
      copy(invocationResults =
        invocationResults + (nodeId -> addResults(invocationResult, invocationResults.getOrElse(nodeId, List())))
      )
    }

    def updateExternalInvocationResult(nodeId: String, contextId: ContextId, name: String, result: Any): TestResults = {
      val invocation = ExternalInvocationResult(contextId.value, name, result)
      copy(externalInvocationResults =
        externalInvocationResults + (nodeId -> (externalInvocationResults.getOrElse(nodeId, List()) :+ invocation))
      )
    }

    def updateExceptionResult(exceptionInfo: NuExceptionInfo[_ <: Throwable]): TestResults =
      copy(exceptions = exceptions :+ exceptionInfo)

    // when evaluating e.g. keyBy expression can be invoked more than once...
    // TODO: is it the best way to handle it??
    private def addResults(
        invocationResult: ExpressionInvocationResult,
        resultsSoFar: List[ExpressionInvocationResult]
    ) = resultsSoFar.filterNot(res =>
      res.contextId == invocationResult.contextId && res.name == invocationResult.name
    ) :+ invocationResult

  }

  /**
    * We have to be careful not to put too much into results, as they are serialized to JSON.
    * TODO: Does it really have any sense? Why not use Context instead of it?
    */
  case class NodeResult(context: Context) {
    def variableTyped[T](name: String): Option[T] = context.get(name)
  }

  case class ExpressionInvocationResult(contextId: String, name: String, value: Any)

  case class ExternalInvocationResult(contextId: String, name: String, value: Any)

}
