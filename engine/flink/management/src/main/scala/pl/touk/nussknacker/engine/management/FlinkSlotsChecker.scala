package pl.touk.nussknacker.engine.management

import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.CoreOptions
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.ClusterOverview

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class FlinkSlotsChecker(client: HttpFlinkClient)(implicit ec: ExecutionContext) extends LazyLogging {

  def checkRequiredSlotsExceedAvailableSlots(canonicalProcess: CanonicalProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = {
    val collectedSlotsCheckInputs = for {
      slotsBalance <- determineSlotsBalance(canonicalProcess, currentlyDeployedJobId)
      clusterOverview <- OptionT(client.getClusterOverview.map(Option(_)))
    } yield (slotsBalance, clusterOverview)

    val checkResult = for {
      collectedInputs <- OptionT(collectedSlotsCheckInputs.value.recover {
        case NonFatal(ex) =>
          logger.warn("Error during collecting inputs needed for available slots checking. Slots checking will be omitted", ex)
          None
      })
      (slotsBalance, clusterOverview) = collectedInputs
      _ <- OptionT(
        if (slotsBalance.value > clusterOverview.`slots-available`)
          Future.failed(NotEnoughSlotsException(clusterOverview, slotsBalance))
        else
          Future.successful(Option(())))
    } yield ()
    checkResult.value.map(_ => Unit)
  }

  private def determineSlotsBalance(canonicalProcess: CanonicalProcess, currentlyDeployedJobId: Option[ExternalDeploymentId]): OptionT[Future, SlotsBalance] = {
    canonicalProcess.metaData.typeSpecificData match {
      case stream: StreamMetaData =>
        val requiredSlotsFuture = for {
          releasedSlots <- slotsThatWillBeReleasedAfterJobCancel(currentlyDeployedJobId)
          allocatedSlots <- slotsAllocatedByProcessThatWilBeDeployed(stream, canonicalProcess.metaData.id)
        } yield Option(SlotsBalance(releasedSlots, allocatedSlots))
        OptionT(requiredSlotsFuture)
      case _ => OptionT.none
    }
  }

  private def slotsThatWillBeReleasedAfterJobCancel(currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Int] = {
    currentlyDeployedJobId
      .map(deploymentId => client.getJobConfig(deploymentId.value).map(_.`job-parallelism`))
      .getOrElse(Future.successful(0))
  }

  private def slotsAllocatedByProcessThatWilBeDeployed(stream: StreamMetaData, processId: String): Future[Int] = {
    stream.parallelism
      .map(definedParallelism => Future.successful(definedParallelism))
      .getOrElse(client.getJobManagerConfig.map { config =>
        val defaultParallelism = config.get(CoreOptions.DEFAULT_PARALLELISM)
        logger.debug(s"Not specified parallelism for process: $processId, will be used default configured on jobmanager: $defaultParallelism")
        defaultParallelism
      })
  }

}

object FlinkSlotsChecker {

  case class NotEnoughSlotsException(availableSlots: Int, totalSlots: Int, slotsBalance: SlotsBalance)
    extends IllegalArgumentException(s"Not enough free slots on Flink cluster. Available slots: $availableSlots, requested: ${Math.max(0, slotsBalance.value)}. ${
      if (slotsBalance.allocated > 1)
        "Decrease scenario's parallelism or extend Flink cluster resources"
      else
        "Extend resources of Flink cluster resources"
    }")

  object NotEnoughSlotsException {
    def apply(clusterOverview: ClusterOverview, slotsBalance: SlotsBalance): NotEnoughSlotsException =
      NotEnoughSlotsException(availableSlots = clusterOverview.`slots-available`, totalSlots = clusterOverview.`slots-total`, slotsBalance = slotsBalance)
  }

  case class SlotsBalance(released: Int, allocated: Int) {
    def value: Int = allocated - released
  }

}
