package pl.touk.nussknacker.engine.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveData
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.time.{Clock, Instant}

private[livedata] class LiveDataCollectingListenerStorage(
    maxNumberOfSamples: Int,
    throughputTimeWindowInSeconds: Int,
)(implicit clock: Clock) {

  private val results            = new RingBuffer[String, TestResults[Json]](maxNumberOfSamples)
  private val transitionsCounter = new SlidingWindowCounter[NodeTransition](Instant.now, throughputTimeWindowInSeconds)

  def getLiveData: LiveData = {
    LiveData(
      liveDataSamples = TestResults.aggregate(results.values),
      nodeTransitionThroughput = transitionsCounter.getThroughput,
    )
  }

  def updateResults(contextId: String, action: TestResults[Json] => TestResults[Json]): Unit = {
    results.update(
      contextId,
      {
        case Some(resultsSoFar) => action(resultsSoFar)
        case None               => action(TestResults.empty[Json])
      }
    )
  }

  def registerTransitionBetweenNodes(from: String, to: Option[String]): Unit = {
    transitionsCounter.add(NodeTransition(from, to))
  }

}
