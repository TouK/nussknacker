package pl.touk.nussknacker.engine.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

import java.time.Instant
import java.util.concurrent.{Executors, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.compat.java8.FunctionConverters.asJavaFunction
import scala.jdk.CollectionConverters._
import scala.util.Try

private[livedata] object LiveDataUploaderHolder {

  private val logger = LoggerFactory.getLogger(getClass)

  // todo: This object will not be shared between Flink jobs, because each scenario has its own isolated memory area and classloader
  //       In future we might want to create a Flink lib that will contain this part of code and uploaders shared between scenarios.
  private val activePeriodicLiveDataUploaders = Caffeine.newBuilder().build[CacheKey, Stoppable]()

  def ensureLiveDataUploaderIsActive(
      processIdWithName: ProcessIdWithName,
      deploymentIdOpt: Option[DeploymentId],
      config: LiveDataUploaderConfig,
  ): Unit = {
    deploymentIdOpt match {
      case Some(deploymentId) =>
        activePeriodicLiveDataUploaders.get(
          CacheKey(processIdWithName, deploymentId),
          asJavaFunction((key: CacheKey) => {
            startPeriodicLiveDataUploader(key.processIdWithName, key.deploymentId, config)
          })
        )
      case None =>
        logger.error(
          "Live data uploader cannot be started, because the DeploymentId is not defined or has non-UUID underlying value"
        )
    }
  }

  private lazy val threadFactory = new BasicThreadFactory.Builder()
    .namingPattern(s"periodic-live-data-uploader")
    .build()

  private def startPeriodicLiveDataUploader(
      processIdWithName: ProcessIdWithName,
      deploymentId: DeploymentId,
      config: LiveDataUploaderConfig,
  ): Stoppable = {
    val uploader                                  = new LiveDataUploader(config)
    val scheduler: ScheduledExecutorService       = Executors.newSingleThreadScheduledExecutor(threadFactory)
    var scheduledTask: Option[ScheduledFuture[_]] = None

    def uploadLiveData(): Unit = {
      logger.debug(logMessage("Periodic live data upload triggered"))
      LiveDataCollectingListenerHolder.storageOpt(processIdWithName.name) match {
        case Some(storage) =>
          val shouldStop =
            storage.getLastUpdatedAt < Instant.now.getEpochSecond - config.uploaderInactivityTimeoutInSeconds
          if (shouldStop) {
            logger.info(logMessage("Stopping live data uploader because of inactivity"))
            stopPeriodicLiveDataUpload()
          } else {
            logger.debug(logMessage("Uploading live data"))
            uploader.uploadLiveData(
              processIdWithName = processIdWithName,
              deploymentId = deploymentId,
              collectedLiveData = storage.getLiveData
            )
          }
        case None =>
          logger.error(logMessage("There are no live data to upload"))
      }
    }

    def stopPeriodicLiveDataUpload(): Unit = {
      tryAndLog(scheduledTask.foreach(_.cancel(true)), logMessage("Error when cancelling live data periodic upload"))
      tryAndLog(scheduler.shutdownNow(), logMessage("Error when shutting down live data uploader periodic upload"))
      tryAndLog(uploader.close(), logMessage("Error when closing live data uploader"))
      activePeriodicLiveDataUploaders.invalidate(CacheKey(processIdWithName, deploymentId))
    }

    def logMessage(message: String) = s"$message for scenario $processIdWithName with deploymentId=$deploymentId"

    logger.info(logMessage(s"Starting live data uploader with interval ${config.intervalSeconds}s on a new thread"))
    scheduledTask = Some(
      scheduler.scheduleAtFixedRate(() => uploadLiveData(), 0, config.intervalSeconds, TimeUnit.SECONDS)
    )
    () => stopPeriodicLiveDataUpload()
  }

  private def tryAndLog(action: => Unit, errorMessage: String): Unit = {
    Try(action).recover { case ex: Throwable => logger.error(errorMessage, ex) }
  }

  private final case class CacheKey(
      processIdWithName: ProcessIdWithName,
      deploymentId: DeploymentId,
  )

  private trait Stoppable {
    def stop(): Unit
  }

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      activePeriodicLiveDataUploaders.asMap().values().asScala.foreach(_.stop())
      activePeriodicLiveDataUploaders.invalidateAll()
    }
  })

}
