package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.{Context, ContextId}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import java.time.Instant

object TestProcess {

  case class TestResults[T](
      nodeResults: Map[String, List[ResultContext[T]]],
      nodeTransitionResults: Map[NodeTransition, List[ResultContext[T]]],
      invocationResults: Map[String, List[ExpressionInvocationResult[T]]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult[T]]],
      exceptions: List[ExceptionResult[T]]
  ) {

    def updateNodeResult(nodeId: String, context: Context, variableEncoder: Any => T): TestResults[T] =
      copy(nodeResults =
        nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ ResultContext
          .fromContext(context, Instant.now(), variableEncoder)))
      )

    def updateNodeOutputResult(
        nodeId: String,
        nextNodeIdOpt: Option[String],
        context: Context,
        variableEncoder: Any => T
    ): TestResults[T] = {
      copy(nodeTransitionResults =
        nodeTransitionResults + (NodeTransition(nodeId, nextNodeIdOpt) ->
          (nodeTransitionResults
            .getOrElse(NodeTransition(nodeId, nextNodeIdOpt), List()) :+ ResultContext
            .fromContext(context, Instant.now(), variableEncoder)))
      )
    }

    def updateExpressionResult(
        nodeId: String,
        context: Context,
        name: String,
        result: Any,
        variableEncoder: Any => T
    ): TestResults[T] = {
      val invocationResult = ExpressionInvocationResult(context.id, Instant.now(), name, variableEncoder(result))
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
      val invocation = ExternalInvocationResult(contextId, Instant.now(), name, variableEncoder(result))
      copy(externalInvocationResults =
        externalInvocationResults + (nodeId -> (externalInvocationResults.getOrElse(nodeId, List()) :+ invocation))
      )
    }

    def updateExceptionResult(
        exceptionInfo: NuExceptionInfo,
        variableEncoder: Any => T
    ): TestResults[T] =
      copy(exceptions =
        exceptions :+ ExceptionResult.fromNuExceptionInfo(exceptionInfo, Instant.now(), variableEncoder)
      )

    // when evaluating e.g. keyBy expression can be invoked more than once...
    // TODO: is it the best way to handle it??
    private def addResults(
        invocationResult: ExpressionInvocationResult[T],
        resultsSoFar: List[ExpressionInvocationResult[T]]
    ): List[ExpressionInvocationResult[T]] = resultsSoFar.filterNot(res =>
      res.contextId == invocationResult.contextId && res.name == invocationResult.name
    ) :+ invocationResult

  }

  object TestResults {

    def empty[T]: TestResults[T] = TestResults[T](Map.empty, Map.empty, Map.empty, Map.empty, List.empty)

    def aggregate[T](testResults: Iterable[TestResults[T]]): TestResults[T] = {
      TestResults[T](
        nodeResults = mergeMaps(testResults.map(_.nodeResults)),
        nodeTransitionResults = mergeMaps(testResults.map(_.nodeTransitionResults)),
        invocationResults = mergeMaps(testResults.map(_.invocationResults)),
        externalInvocationResults = mergeMaps(testResults.map(_.externalInvocationResults)),
        exceptions = testResults.flatMap(_.exceptions).toList,
      )
    }

    private def mergeMaps[K, V](listOfMaps: Iterable[Map[K, List[V]]]): Map[K, List[V]] = {
      listOfMaps.foldLeft(Map.empty[K, List[V]]) { case (acc, map) =>
        map.foldLeft(acc) { case (innerAcc, (key, value)) =>
          innerAcc.updated(key, innerAcc.getOrElse(key, Nil) ++ value)
        }
      }
    }

  }

  final case class NodeTransition(sourceNodeId: String, destinationNodeId: Option[String])

  case class ExpressionInvocationResult[T](contextId: ContextId, timestamp: Instant, name: String, value: T)

  case class ExternalInvocationResult[T](contextId: ContextId, timestamp: Instant, name: String, value: T)

  object ExceptionResult {

    def fromNuExceptionInfo[T](
        exceptionInfo: NuExceptionInfo,
        timestamp: Instant,
        variableEncoder: Any => T
    ): ExceptionResult[T] =
      ExceptionResult(
        ResultContext.fromContext(exceptionInfo.context, timestamp, variableEncoder),
        exceptionInfo.nodeComponentInfo.map(_.nodeId),
        exceptionInfo.throwable
      )

  }

  case class ExceptionResult[T](context: ResultContext[T], nodeId: Option[String], throwable: Throwable)

  object ResultContext {
    def fromContext[T](context: Context, timestamp: Instant, variableEncoder: Any => T): ResultContext[T] =
      ResultContext(context.id, timestamp, context.variables.map { case (k, v) => k -> variableEncoder(v) })
  }

  // We don't pass here traceId - it's intentional
  case class ResultContext[T](id: ContextId, timestamp: Instant, variables: Map[String, T]) {
    def variableTyped[U <: T](name: String): Option[U] = variables.get(name).map(_.asInstanceOf[U])
  }

}
