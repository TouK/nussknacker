package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.{Context, ContextId, NodeId}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import java.time.Instant

object TestProcess {

  case class TestResults[T](
      nodeResults: Map[NodeId, List[ResultContext[T]]],
      nodeTransitionResults: Map[NodeTransition, List[ResultContext[T]]],
      expressionEvaluationResults: Map[NodeId, List[ExpressionEvaluationResult[T]]],
      externalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult[T]]],
      exceptions: List[ExceptionResult[T]],
      originalNodeResults: Map[NodeId, List[
        ResultContext[Any]
      ]],
  ) {

    def updateNodeResult(nodeId: NodeId, context: Context, variableEncoder: Any => T): TestResults[T] = {
      val instantNow = Instant.now()
      copy(
        nodeResults = nodeResults + (nodeId -> (nodeResults.getOrElse(nodeId, List()) :+ ResultContext
          .fromContext(context, instantNow, variableEncoder))),
        originalNodeResults =
          originalNodeResults + (nodeId -> (originalNodeResults.getOrElse(nodeId, List()) :+ ResultContext
            .fromContext(context, instantNow, identity))),
      )
    }

    def updateNodeOutputResult(
        nodeId: NodeId,
        nextNodeIdOpt: Option[NodeId],
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
        nodeId: NodeId,
        context: Context,
        name: String,
        result: Any,
        variableEncoder: Any => T
    ): TestResults[T] = {
      val invocationResult = ExpressionEvaluationResult(context.id, Instant.now(), name, variableEncoder(result))
      copy(expressionEvaluationResults =
        expressionEvaluationResults + (nodeId -> addResults(
          invocationResult,
          expressionEvaluationResults.getOrElse(nodeId, List())
        ))
      )
    }

    def updateExternalInvocationResult(
        nodeId: NodeId,
        contextId: ContextId,
        name: String,
        result: Any,
        variableEncoder: Any => T
    ): TestResults[T] = {
      val invocation = ExternalServiceInvocationResult(contextId, Instant.now(), name, variableEncoder(result))
      copy(externalServiceInvocationResults =
        externalServiceInvocationResults + (nodeId -> (externalServiceInvocationResults
          .getOrElse(nodeId, List()) :+ invocation))
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
        invocationResult: ExpressionEvaluationResult[T],
        resultsSoFar: List[ExpressionEvaluationResult[T]]
    ): List[ExpressionEvaluationResult[T]] = resultsSoFar.filterNot(res =>
      res.contextId == invocationResult.contextId && res.name == invocationResult.name
    ) :+ invocationResult

  }

  object TestResults {

    def empty[T]: TestResults[T] =
      TestResults[T](Map.empty, Map.empty, Map.empty, Map.empty, List.empty, Map.empty)

    def aggregate[T](testResults: Iterable[TestResults[T]]): TestResults[T] = {
      TestResults[T](
        nodeResults = mergeMaps(testResults.map(_.nodeResults)),
        nodeTransitionResults = mergeMaps(testResults.map(_.nodeTransitionResults)),
        expressionEvaluationResults = mergeMaps(testResults.map(_.expressionEvaluationResults)),
        externalServiceInvocationResults = mergeMaps(testResults.map(_.externalServiceInvocationResults)),
        exceptions = testResults.flatMap(_.exceptions).toList,
        originalNodeResults = mergeMaps(testResults.map(_.originalNodeResults)),
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

  final case class NodeTransition(sourceNodeId: NodeId, destinationNodeId: Option[NodeId])

  case class ExpressionEvaluationResult[T](contextId: ContextId, timestamp: Instant, name: String, value: T)

  case class ExternalServiceInvocationResult[T](contextId: ContextId, timestamp: Instant, name: String, value: T)

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

  case class ExceptionResult[T](context: ResultContext[T], nodeId: Option[NodeId], throwable: Throwable)

  object ResultContext {
    def fromContext[T](context: Context, timestamp: Instant, variableEncoder: Any => T): ResultContext[T] =
      ResultContext(context.id, timestamp, context.variables.map { case (k, v) => k -> variableEncoder(v) })
  }

  case class ResultContext[T](id: ContextId, timestamp: Instant, variables: Map[String, T]) {
    def variableTyped[U <: T](name: String): Option[U] = variables.get(name).map(_.asInstanceOf[U])
  }

}
