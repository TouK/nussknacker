package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, ContextId}

object TestProcess {

  case class TestResults[T](
      nodeResults: Map[String, List[ResultContext[T]]],
      invocationResults: Map[String, List[ExpressionInvocationResult[T]]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult[T]]],
      exceptions: List[ExceptionResult[T]],
      variableEncoder: Any => T
  ) {

    def updateNodeResult(nodeId: String, context: Context): TestResults[T] =
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ toResult(context))))

    def updateExpressionResult(
        nodeId: String,
        context: Context,
        name: String,
        result: Any
    ): TestResults[T] = {
      val invocationResult = ExpressionInvocationResult(context.id, name, variableEncoder(result))
      copy(invocationResults =
        invocationResults + (nodeId -> addResults(invocationResult, invocationResults.getOrElse(nodeId, List())))
      )
    }

    def updateExternalInvocationResult(
        nodeId: String,
        contextId: ContextId,
        name: String,
        result: Any
    ): TestResults[T] = {
      val invocation = ExternalInvocationResult(contextId.value, name, variableEncoder(result))
      copy(externalInvocationResults =
        externalInvocationResults + (nodeId -> (externalInvocationResults.getOrElse(nodeId, List()) :+ invocation))
      )
    }

    def updateExceptionResult(exceptionInfo: NuExceptionInfo[_ <: Throwable]): TestResults[T] =
      copy(exceptions =
        exceptions :+ ExceptionResult(
          toResult(exceptionInfo.context),
          exceptionInfo.nodeComponentInfo.map(_.nodeId),
          exceptionInfo.throwable
        )
      )

    // when evaluating e.g. keyBy expression can be invoked more than once...
    // TODO: is it the best way to handle it??
    private def addResults(
        invocationResult: ExpressionInvocationResult[T],
        resultsSoFar: List[ExpressionInvocationResult[T]]
    ): List[ExpressionInvocationResult[T]] = resultsSoFar.filterNot(res =>
      res.contextId == invocationResult.contextId && res.name == invocationResult.name
    ) :+ invocationResult

    private def toResult(context: Context): ResultContext[T] =
      ResultContext(context.id, context.variables.map { case (k, v) => k -> variableEncoder(v) })

  }

  case class ExpressionInvocationResult[T](contextId: String, name: String, value: T)

  case class ExternalInvocationResult[T](contextId: String, name: String, value: T)

  case class ExceptionResult[T](context: ResultContext[T], nodeId: Option[String], throwable: Throwable)

  case class ResultContext[T](id: String, variables: Map[String, T])

}
