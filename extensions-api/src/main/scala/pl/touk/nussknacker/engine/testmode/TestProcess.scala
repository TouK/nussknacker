package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, ContextId}

object TestProcess {

  case class TestResults[T](nodeResults: Map[String, List[NodeResult[T]]],
                            invocationResults: Map[String, List[ExpressionInvocationResult[T]]],
                            externalInvocationResults: Map[String, List[ExternalInvocationResult[T]]],
                            exceptions: List[ExceptionResult[T]], variableEncoder: Any => T) {

    def updateNodeResult(nodeId: String, context: Context) = {
      copy(nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ NodeResult(toResult(context)))))
    }

    def updateExpressionResult(nodeId: String, context: Context, name: String, result: Any) = {
      val invocationResult = ExpressionInvocationResult(context.id, name, variableEncoder(result))
      copy(invocationResults = invocationResults + (nodeId -> addResults(invocationResult, invocationResults.getOrElse(nodeId, List()))))
    }

    def updateExternalInvocationResult(nodeId: String, contextId: ContextId, name: String, result: Any) = {
      val invocation = ExternalInvocationResult(contextId.value, name, variableEncoder(result))
      copy(externalInvocationResults = externalInvocationResults + (nodeId -> (externalInvocationResults.getOrElse(nodeId, List()) :+ invocation)))
    }

    def updateExceptionResult(espExceptionInfo: NuExceptionInfo[_ <: Throwable]) = {
      copy(exceptions = exceptions :+ ExceptionResult(toResult(espExceptionInfo.context), espExceptionInfo.nodeComponentInfo.map(_.nodeId), espExceptionInfo.throwable))
    }

    //when evaluating e.g. keyBy expression can be invoked more than once...
    //TODO: is it the best way to handle it??
    private def addResults(invocationResult: ExpressionInvocationResult[T], resultsSoFar: List[ExpressionInvocationResult[T]])
    = resultsSoFar.filterNot(res => res.contextId == invocationResult.contextId && res.name == invocationResult.name) :+ invocationResult

    private def toResult(context: Context): ResultContext[T] = ResultContext(context.id, context.variables.map { case (k, v) => k -> variableEncoder(v) })


  }

  /*
    We have to be careful not to put too much into results, as they are serialized to JSON.
   */
  case class NodeResult[T](context: ResultContext[T]) {

    def variableTyped[U <: T](name: String): Option[U] = context.variableTyped(name)

  }

  case class ExpressionInvocationResult[T](contextId: String, name: String, value: T)

  case class ExternalInvocationResult[T](contextId: String, name: String, value: T)

  case class ExceptionResult[T](context: ResultContext[T], nodeId: Option[String], throwable: Throwable)

  case class ResultContext[T](id: String,  variables: Map[String, T]) {

    def variableTyped[U <: T](name: String): Option[U] = variables.get(name).map(_.asInstanceOf[U])

  }

}
