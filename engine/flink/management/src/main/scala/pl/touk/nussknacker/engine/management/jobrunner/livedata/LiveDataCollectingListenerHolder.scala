package pl.touk.nussknacker.engine.management.jobrunner.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveDataPreview
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.compat.java8.FunctionConverters.asJavaFunction

object LiveDataCollectingListenerHolder {

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
    // We want to store and present only the live data from the current deployment,
    // so when we start a new job, we discard all old data
    cleanResults(processName)
    new LiveDataCollectingListener(processName, maxNumberOfSamples, throughputTimeWindowInSeconds)
  }

  def getLiveDataPreview(processName: ProcessName): Option[LiveDataPreview] = {
    Option(listenerStorages.getIfPresent(processName.value)).map(_.getLiveDataPreview)
  }

  private[livedata] def cleanResults(processName: ProcessName): Unit = {
    listenerStorages.invalidate(processName.value)
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

}
