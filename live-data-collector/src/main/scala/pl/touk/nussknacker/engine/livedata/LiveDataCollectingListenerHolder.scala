package pl.touk.nussknacker.engine.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

import java.time.Clock
import java.util.UUID
import scala.compat.java8.FunctionConverters._

object LiveDataCollectingListenerHolder {

  val id: UUID = UUID.randomUUID()

  private implicit val clock: Clock = Clock.systemUTC()

  private val listenerStorages =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofHours(1))
      .build[String, LiveDataCollectingListenerStorage]()

  def createListenerFor(
      processIdWithName: ProcessIdWithName,
      // todo: option can be removed, when we fully migrate to newdeployment.DeploymentId
      deploymentIdOpt: Option[DeploymentId],
      config: Option[LiveDataUploaderConfig],
      maxNumberOfRecords: Int,
      throughputTimeWindowInSeconds: Int,
  ): LiveDataCollectingListener = {
    cleanResults(processIdWithName.name)
    new LiveDataCollectingListener(
      processIdWithName,
      deploymentIdOpt,
      config,
      maxNumberOfRecords,
      throughputTimeWindowInSeconds
    )
  }

  def getLiveDataPreview(processName: ProcessName): Option[CollectedLiveData] = {
    Option(listenerStorages.getIfPresent(processName.value)).map(_.getLiveData)
  }

  private[livedata] def performStorageOperation(
      processIdWithName: ProcessIdWithName,
      deploymentId: Option[DeploymentId],
      config: Option[LiveDataUploaderConfig],
      maxNumberOfRecords: Int,
      throughputTimeWindowInSeconds: Int,
  )(f: LiveDataCollectingListenerStorage => Unit): Unit = {
    f(storage(processIdWithName.name, maxNumberOfRecords, throughputTimeWindowInSeconds))
    config.foreach(LiveDataUploaderHolder.ensureLiveDataUploaderIsActive(processIdWithName, deploymentId, _))
  }

  private def storage(
      processName: ProcessName,
      maxNumberOfRecords: Int,
      throughputTimeWindowInSeconds: Int,
  ): LiveDataCollectingListenerStorage = {
    listenerStorages.get(
      processName.value,
      asJavaFunction((_: String) =>
        new LiveDataCollectingListenerStorage(maxNumberOfRecords, throughputTimeWindowInSeconds)
      )
    )
  }

  private[livedata] def storageOpt(
      processName: ProcessName,
  ): Option[LiveDataCollectingListenerStorage] = {
    Option(listenerStorages.getIfPresent(processName.value))
  }

  // We want to store and present the live data from the most recent deployment:
  //  - the data from the old run is stored until the listener storage cache expires (currently hardcoded 1 hour)
  //  - or new deployment is done - then we discard all old data
  private def cleanResults(processName: ProcessName): Unit = {
    listenerStorages.invalidate(processName.value)
  }

}
