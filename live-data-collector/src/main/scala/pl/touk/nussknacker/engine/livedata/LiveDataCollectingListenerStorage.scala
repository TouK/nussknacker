package pl.touk.nussknacker.engine.livedata

import pl.touk.nussknacker.engine.api.NodeId

import java.time.{Clock, Instant}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

private[livedata] class LiveDataCollectingListenerStorage(
    maxNumberOfRecords: Int,
    throughputTimeWindowInSeconds: Int,
)(implicit clock: Clock) {

  private val lastUpdatedAt = new AtomicLong(Instant.now.getEpochSecond)

  private val samples = new ConcurrentHashMap[NodeTransition, RingBufferWithTotalCount[LiveDataSample]]

  private val invocationResults = new ConcurrentHashMap[NodeId, RingBufferWithTotalCount[InvocationResult]]

  private val externalInvocations = new ConcurrentHashMap[NodeId, RingBufferWithTotalCount[InvocationResult]]

  private val exceptions = new ConcurrentHashMap[NodeId, RingBufferWithTotalCount[ExceptionResult]]

  private val transitionsSlidingWindowCounter: SlidingWindowCounter[NodeTransition] =
    new SlidingWindowCounter[NodeTransition](Instant.now, throughputTimeWindowInSeconds)

  def getLastUpdatedAt: Long = lastUpdatedAt.get()

  def getLiveData: CollectedLiveData = {
    CollectedLiveData(
      timestamp = Instant.now,
      nodeTransitions = samples.asScala.toMap.map { case (transition, values) =>
        transition -> LiveDataForNodeTransition(
          samples = values.values,
          totalCount = values.totalCount,
          currentThroughput = transitionsSlidingWindowCounter.getThroughput.getOrElse(transition, 0)
        )
      },
      invocationResults = invocationResults.asScala.toMap.map { case (nodeId, values) =>
        nodeId -> values.values
      },
      externalInvocationResults = externalInvocations.asScala.toMap.map { case (nodeId, values) =>
        nodeId -> values.values
      },
      exceptions = exceptions.asScala.toMap.map { case (nodeId, values) =>
        nodeId -> values.values
      },
    )
  }

  def addLiveDataSample(nodeTransition: NodeTransition, liveDataSample: LiveDataSample): Unit = {
    transitionsSlidingWindowCounter.add(nodeTransition)
    put(samples, nodeTransition, liveDataSample)
  }

  def addExpressionEvaluation(nodeId: NodeId, value: InvocationResult): Unit = {
    put(invocationResults, nodeId, value)
  }

  def addExternalInvocation(nodeId: NodeId, value: InvocationResult): Unit = {
    put(externalInvocations, nodeId, value)
  }

  def addException(nodeId: NodeId, value: ExceptionResult): Unit = {
    put(exceptions, nodeId, value)
  }

  private def put[K, V](
      storage: ConcurrentHashMap[K, RingBufferWithTotalCount[V]],
      key: K,
      value: V,
  ): Unit = {
    lastUpdatedAt.set(clock.instant().getEpochSecond)
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
