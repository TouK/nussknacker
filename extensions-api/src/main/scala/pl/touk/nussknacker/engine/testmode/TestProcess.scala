package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

object TestProcess {

  case class TestResults[T](
      nodeResults: Map[String, List[ResultContext[T]]],
      invocationResults: Map[String, List[ExpressionInvocationResult[T]]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult[T]]],
      exceptions: List[ExceptionResult[T]]
  ) {

    def updateNodeResult(nodeId: String, context: Context, variableEncoder: Any => T): TestResults[T] =
      copy(nodeResults =
        nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ ResultContext
          .fromContext(context, variableEncoder)))
      )

    def updateExpressionResult(
        nodeId: String,
        context: Context,
        name: String,
        result: Any,
        variableEncoder: Any => T
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
        result: Any,
        variableEncoder: Any => T
    ): TestResults[T] = {
      val invocation = ExternalInvocationResult(contextId.value, name, variableEncoder(result))
      copy(externalInvocationResults =
        externalInvocationResults + (nodeId -> (externalInvocationResults.getOrElse(nodeId, List()) :+ invocation))
      )
    }

    def updateExceptionResult(
        exceptionInfo: NuExceptionInfo[_ <: Throwable],
        variableEncoder: Any => T
    ): TestResults[T] =
      copy(exceptions = exceptions :+ ExceptionResult.fromNuExceptionInfo(exceptionInfo, variableEncoder))

    // when evaluating e.g. keyBy expression can be invoked more than once...
    // TODO: is it the best way to handle it??
    private def addResults(
        invocationResult: ExpressionInvocationResult[T],
        resultsSoFar: List[ExpressionInvocationResult[T]]
    ): List[ExpressionInvocationResult[T]] = resultsSoFar.filterNot(res =>
      res.contextId == invocationResult.contextId && res.name == invocationResult.name
    ) :+ invocationResult

  }

  case class ExpressionInvocationResult[T](contextId: String, name: String, value: T)

  case class ExternalInvocationResult[T](contextId: String, name: String, value: T)

  object ExceptionResult {

    def fromNuExceptionInfo[T](
        exceptionInfo: NuExceptionInfo[_ <: Throwable],
        variableEncoder: Any => T
    ): ExceptionResult[T] =
      ExceptionResult(
        ResultContext.fromContext(exceptionInfo.context, variableEncoder),
        exceptionInfo.nodeComponentInfo.map(_.nodeId),
        exceptionInfo.throwable
      )

  }

  case class ExceptionResult[T](context: ResultContext[T], nodeId: Option[String], throwable: Throwable)

  object ResultContext {
    def fromContext[T](context: Context, variableEncoder: Any => T): ResultContext[T] =
      ResultContext(context.id, context.variables.map { case (k, v) => k -> variableEncoder(v) })
  }

  case class ResultContext[T](id: String, variables: Map[String, T]) {
    def variableTyped[U <: T](name: String): Option[U] = variables.get(name).map(_.asInstanceOf[U])
  }

}
