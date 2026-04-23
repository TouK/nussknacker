package pl.touk.nussknacker.test.containers

import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.testcontainers.DockerClientFactory

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * Like [[org.testcontainers.utility.ResourceReaper]], but for Volumes.
 *
 * Ideally we'd use tags and depend on the main `ResourceReaper`, but it works with labels, and that would complicate
 * volume creation which then would need to happen explicitly via `DockerClient.createVolumeCmd().withLabels(...)`.
 *
 * See https://github.com/testcontainers/testcontainers-java/issues/7420 for discussion
 * about adding volumes to Testcontainers.
 */
object VolumeReaper extends LazyLogging {

  Runtime.getRuntime.addShutdownHook(
    new Thread(DockerClientFactory.TESTCONTAINERS_THREAD_GROUP, () => this.performCleanup(), "volumes-reaper")
  )

  private val volumes = ConcurrentHashMap.newKeySet[String]

  private def performCleanup(): Unit = {
    removeVolumesQuietly(volumes.asScala.toSeq)
  }

  def registerVolume(name: String): Unit = this.synchronized {
    volumes.add(name)
  }

  def unregisterVolume(name: String): Unit = this.synchronized {
    volumes.remove(name)
  }

  def removeVolumesQuietly(volumeNames: Seq[String], rmLogger: Logger = logger): Unit = this.synchronized {
    if (volumeNames.nonEmpty) {
      try {
        val client              = DockerClientFactory.instance().client()
        val existingVolumes     = client.listVolumesCmd().exec().getVolumes.asScala.map(_.getName)
        val (toRemove, missing) = existingVolumes.partition(volumeNames.contains)
        missing.foreach(unregisterVolume)
        toRemove.foreach { volumeName =>
          try {
            client.removeVolumeCmd(volumeName).exec()
            unregisterVolume(volumeName)
          } catch {
            case NonFatal(e) => rmLogger.warn(s"Failed to remove Docker volume $volumeName: ${e.getMessage}")
          }
        }
      } catch {
        case NonFatal(e) => rmLogger.warn(s"Failed to remove Docker volumes: ${volumeNames.mkString(", ")}", e)
      }
    }
  }

}
