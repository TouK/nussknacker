package pl.touk.nussknacker.engine.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.{LiveData, LiveDataError}
import pl.touk.nussknacker.engine.api.process.ProcessName

import java.time.Clock
import scala.compat.java8.FunctionConverters._

object LiveDataCollectingListenerHolder {

  private implicit val clock: Clock = Clock.systemUTC()

  private val listenerStorages =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofHours(1))
      .build[String, LiveDataCollectingListenerStorage]()

  def createListenerFor(
      processName: ProcessName,
      maxNumberOfSamples: Int,
      throughputTimeWindowInSeconds: Int,
  ): LiveDataCollectingListener = {
    cleanResults(processName)
    new LiveDataCollectingListener(processName, maxNumberOfSamples, throughputTimeWindowInSeconds)
  }

  def getLiveDataPreview(processName: ProcessName): Either[LiveDataError, LiveData] = {
    Option(listenerStorages.getIfPresent(processName.value)).map(_.getLiveData) match {
      case Some(liveData) => Right(liveData)
      case None           => Left(LiveDataError.NoLiveDataAvailableForScenario)
    }
  }

  private[livedata] def storage(
      processName: ProcessName,
      maxNumberOfSamples: Int,
      throughputTimeWindowInSeconds: Int,
  ): LiveDataCollectingListenerStorage = {
    listenerStorages.get(
      processName.value,
      asJavaFunction((_: String) =>
        new LiveDataCollectingListenerStorage(maxNumberOfSamples, throughputTimeWindowInSeconds)
      )
    )
  }

  // We want to store and present the live data from the most recent deployment:
  //  - the data from the old run is stored until the listener storage cache expires (currently hardcoded 1 hour)
  //  - or new deployment is done - then we discard all old data
  private def cleanResults(processName: ProcessName): Unit = {
    listenerStorages.invalidate(processName.value)
  }

}
