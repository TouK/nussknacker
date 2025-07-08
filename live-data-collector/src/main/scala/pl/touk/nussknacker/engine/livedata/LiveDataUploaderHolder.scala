package pl.touk.nussknacker.engine.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.compat.java8.FunctionConverters.asJavaFunction

private[livedata] object LiveDataUploaderHolder {

  private val activeUploaders =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofHours(1))
      .build[
        CacheKey,
        String
      ]() // Should be [(ProcessIdWithName, DeploymentId), Unit], but Scala 2.12 does not accept tuples and Unit

  private val logger = LoggerFactory.getLogger(getClass)

  def getExistingOrStartLiveDataUploader(
      processIdWithName: ProcessIdWithName,
      deploymentIdOpt: Option[DeploymentId],
      config: LiveDataUploaderConfig,
  ): Unit = {
    deploymentIdOpt match {
      case Some(deploymentId) =>
        activeUploaders.get(
          CacheKey(processIdWithName, deploymentId),
          asJavaFunction((cacheKey: CacheKey) => {
            startPeriodicLiveDataUploader(cacheKey.processIdWithName, cacheKey.deploymentId, config)
            ""
          })
        )
      case None =>
        logger.error(
          "Live data uploader cannot be started, because the DeploymentId is not defined or has non-UUID underlying value"
        )
    }
  }

  def startPeriodicLiveDataUploader(
      processIdWithName: ProcessIdWithName,
      deploymentId: DeploymentId,
      config: LiveDataUploaderConfig,
  ): Unit = {
    logger.info(s"Starting live data uploader for scenario $processIdWithName with interval ${config.intervalSeconds}s")
    val uploader                                  = new LiveDataUploader(config)
    val scheduler: ScheduledExecutorService       = Executors.newSingleThreadScheduledExecutor()
    var scheduledTask: Option[ScheduledFuture[_]] = None

    def uploadLiveData(): Unit = {
      LiveDataCollectingListenerHolder
        .storageOpt(processIdWithName.name)
        .foreach { storage =>
          // Arbitrary interval - if there are no new events for 5 minutes, then uploader is stopped.
          // It will be started again when there is a new event.
          val shouldStop =
            storage.getLastUpdatedAt < Instant.now.getEpochSecond - config.uploaderInactivityTimeoutInSeconds
          if (shouldStop) {
            logger.info(s"Stopping live data uploader for scenario $processIdWithName because of inactivity")
            scheduledTask.foreach(_.cancel(true))
            scheduler.shutdownNow()
            activeUploaders.invalidate(CacheKey(processIdWithName, deploymentId))
          } else {
            uploader.uploadLiveData(
              processIdWithName = processIdWithName,
              deploymentId = deploymentId,
              collectedLiveData = storage.getLiveData
            )
          }
        }
    }

    scheduledTask = Some(
      scheduler.scheduleAtFixedRate(() => uploadLiveData(), 0, config.intervalSeconds, TimeUnit.SECONDS)
    )
  }

  private final case class CacheKey(processIdWithName: ProcessIdWithName, deploymentId: DeploymentId)

}
