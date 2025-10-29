package pl.touk.nussknacker.engine.livedata

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerStorage.NodeParameter

import java.time.{Clock, Instant}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._
import scala.math.Ordered.orderingToOrdered

private[livedata] class LiveDataCollectingListenerStorage(
    maxNumberOfRecords: Int,
    retentionTimeInMinutes: Int,
    throughputTimeWindowInSeconds: Int,
)(implicit clock: Clock) {

  private val lastUpdatedAt = new AtomicReference(Instant.now)

  private val samples = new ConcurrentHashMap[NodeTransition, RingBufferWithTotalCount[LiveDataSample]]

  private val expressionEvaluationResults =
    new ConcurrentHashMap[NodeParameter, RingBufferWithTotalCount[ExpressionEvaluationResult]]

  private val externalServiceInvocationResults =
    new ConcurrentHashMap[NodeParameter, RingBufferWithTotalCount[ExternalServiceInvocationResult]]

  private val exceptions = new ConcurrentHashMap[NodeId, RingBufferWithTotalCount[ExceptionResult]]

  private val transitionsSlidingWindowCounter: SlidingWindowCounter[NodeTransition] =
    new SlidingWindowCounter[NodeTransition](Instant.now, throughputTimeWindowInSeconds)

  def getLastUpdatedAt: Instant = lastUpdatedAt.get()

  def getLiveData: CollectedLiveData = {
    CollectedLiveData(
      timestamp = Instant.now,
      nodeTransitions = samples.asScala.toMap.map { case (transition, values) =>
        transition -> LiveDataForNodeTransition(
          samples = latestValues[LiveDataSample](values, _.timestamp),
          totalCount = values.totalCount,
          currentThroughput = transitionsSlidingWindowCounter.getThroughput.getOrElse(transition, 0)
        )
      },
      expressionEvaluationResults = expressionEvaluationResults.asScala.toMap
        .groupBy { case (nodeExpr, _) => nodeExpr.nodeId }
        .map { case (nodeId, matchingValuesMap) =>
          nodeId -> matchingValuesMap.values.toList
            .flatMap(latestValues[ExpressionEvaluationResult](_, _.timestamp))
        },
      externalServiceInvocationResults = externalServiceInvocationResults.asScala.toMap
        .groupBy { case (nodeExpr, _) => nodeExpr.nodeId }
        .map { case (nodeId, matchingValuesMap) =>
          nodeId -> matchingValuesMap.values.toList
            .flatMap(latestValues[ExternalServiceInvocationResult](_, _.timestamp))
        },
      exceptions = exceptions.asScala.toMap.map { case (nodeId, values) =>
        nodeId -> latestValues[ExceptionResult](values, _.timestamp)
      },
    )
  }

  def addLiveDataSample(nodeTransition: NodeTransition, liveDataSample: LiveDataSample): Unit = {
    transitionsSlidingWindowCounter.add(nodeTransition)
    put(samples, nodeTransition, liveDataSample)
  }

  def addExpressionEvaluationResult(nodeId: NodeId, value: ExpressionEvaluationResult): Unit = {
    put(expressionEvaluationResults, NodeParameter(nodeId, value.name), value)
  }

  def addExternalServiceInvocation(nodeId: NodeId, value: ExternalServiceInvocationResult): Unit = {
    put(externalServiceInvocationResults, NodeParameter(nodeId, value.name), value)
  }

  def addException(nodeId: NodeId, value: ExceptionResult): Unit = {
    put(exceptions, nodeId, value)
  }

  private def latestValues[T](values: RingBufferWithTotalCount[T], timestampExtractor: T => Instant): List[T] = {
    val cutoff = Instant.now.minusSeconds(retentionTimeInMinutes * 60)
    values.values.filter(record => timestampExtractor(record) >= cutoff)
  }

  private def put[K, V](
      storage: ConcurrentHashMap[K, RingBufferWithTotalCount[V]],
      key: K,
      value: V,
  ): Unit = {
    lastUpdatedAt.set(clock.instant())
    storage.compute(
      key,
      (_: K, valuesOpt: RingBufferWithTotalCount[V]) => {
        val values = Option(valuesOpt) match {
          case Some(values) => values
          case None         => new RingBufferWithTotalCount[V](maxNumberOfRecords)
        }
        values.put(value)
        values
      }
    )
  }

}

object LiveDataCollectingListenerStorage {
  private final case class NodeParameter(nodeId: NodeId, parameterName: String)
}
