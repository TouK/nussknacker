package pl.touk.nussknacker.engine.management.jobrunner.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveDataPreview
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

private[livedata] class LiveDataCollectingListenerStorage(
    maxNumberOfSamples: Int,
    frequencyWindowInSeconds: Int,
) {

  private type K = Context
  private type V = TestResults[Json]

  private val results                             = new ConcurrentHashMap[K, V]()
  private val orderOfResults                      = new ConcurrentLinkedQueue[K]()
  private val transitionsByEpochSecond            = new ConcurrentHashMap[Long, ConcurrentLinkedQueue[NodeTransition]]()
  private val transitionsLastCleanedInEpochSecond = new AtomicLong(0)

  def getLiveDataPreview: LiveDataPreview = LiveDataPreview(getAggregatedResults, getTransitionFrequencies)

  def updateResults(context: Context, action: TestResults[Json] => TestResults[Json]): Unit = {
    compute(
      context,
      (_: Context, maybeOldResults: Option[TestResults[Json]]) => {
        val oldResults = maybeOldResults match {
          case Some(results) =>
            results
          case None =>
            TestResults[Json](Map.empty, Map.empty, Map.empty, Map.empty, List.empty)
        }
        val newResults = action(oldResults)
        newResults
      }
    )
  }

  def registerTransitionBetweenNodes(from: String, to: Option[String]): Unit = {
    val epochSecond = Instant.now.getEpochSecond
    val bucket = transitionsByEpochSecond.computeIfAbsent(epochSecond, _ => new ConcurrentLinkedQueue[NodeTransition]())
    bucket.add(NodeTransition(from, to))

    if (transitionsLastCleanedInEpochSecond.get() != epochSecond) {
      transitionsLastCleanedInEpochSecond.set(epochSecond)
      cleanOldTransitions(epochSecond)
    }
  }

  private def compute(key: K, remappingFunction: (K, Option[V]) => V): V = synchronized {
    val currentValue = Option(results.get(key))
    val newValue     = remappingFunction(key, currentValue)
    val isNewKey     = !results.containsKey(key)
    results.put(key, newValue)
    if (isNewKey) {
      orderOfResults.add(key)
      while (orderOfResults.size() > maxNumberOfSamples) {
        val oldest = orderOfResults.poll()
        if (oldest != null) results.remove(oldest)
      }
    }
    newValue
  }

  private def getAggregatedResults: V =
    TestResults.aggregate(orderOfResults.asScala.toList.flatMap(key => Option(results.get(key))))

  private def getTransitionFrequencies: Map[NodeTransition, BigDecimal] = {
    val cutoff = Instant.now().getEpochSecond - frequencyWindowInSeconds
    transitionsByEpochSecond.asScala
      .filter { case (epoch, _) => epoch >= cutoff }
      .values
      .flatMap(_.asScala)
      .groupBy(identity)
      .view
      .mapValues(transitions =>
        BigDecimal(transitions.size)./(frequencyWindowInSeconds).setScale(4, BigDecimal.RoundingMode.HALF_UP)
      )
      .toMap
  }

  private def cleanOldTransitions(currentEpochSecond: Long): Unit = {
    val cutoff = currentEpochSecond - frequencyWindowInSeconds
    transitionsByEpochSecond
      .keySet()
      .asScala
      .filter(_ < cutoff)
      .foreach(transitionsByEpochSecond.remove)
  }

}
