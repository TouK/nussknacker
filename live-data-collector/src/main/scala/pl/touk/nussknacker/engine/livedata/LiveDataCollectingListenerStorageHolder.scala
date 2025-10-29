package pl.touk.nussknacker.engine.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.process.ProcessName

import java.time.Clock
import scala.compat.java8.FunctionConverters._

object LiveDataCollectingListenerStorageHolder {

  private implicit val clock: Clock = Clock.systemUTC()

  private val listenerStorages =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofHours(1))
      .build[ProcessName, LiveDataCollectingListenerStorage]()

  def getLiveDataPreview(processName: ProcessName): Option[CollectedLiveData] = {
    Option(listenerStorages.getIfPresent(processName)).map(_.getLiveData)
  }

  private[livedata] def withStorage(
      processName: ProcessName,
      maxNumberOfRecords: Int,
      retentionTimeInMinutes: Int,
      throughputTimeWindowInSeconds: Int,
  )(actionOnStorage: LiveDataCollectingListenerStorage => Unit): Unit = {
    val storage = listenerStorages.get(
      processName,
      asJavaFunction((_: ProcessName) =>
        new LiveDataCollectingListenerStorage(maxNumberOfRecords, retentionTimeInMinutes, throughputTimeWindowInSeconds)
      )
    )
    actionOnStorage(storage)
  }

  private[livedata] def storageOpt(
      processName: ProcessName,
  ): Option[LiveDataCollectingListenerStorage] = {
    Option(listenerStorages.getIfPresent(processName))
  }

  // We want to store and present the live data from the most recent deployment:
  //  - the data from the old run is stored until the listener storage cache expires (currently hardcoded 1 hour)
  //  - or new deployment is done - then we discard all old data
  private[livedata] def cleanResults(processName: ProcessName): Unit = {
    listenerStorages.invalidate(processName)
  }

}
