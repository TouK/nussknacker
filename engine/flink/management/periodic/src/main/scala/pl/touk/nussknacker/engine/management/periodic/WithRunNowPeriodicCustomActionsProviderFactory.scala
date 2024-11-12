package pl.touk.nussknacker.engine.management.periodic

import cats.data.OptionT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.{CustomActionDefinition, CustomActionResult}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository
import pl.touk.nussknacker.engine.api.deployment.DMCustomActionCommand
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName.RunNow

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class WithRunNowPeriodicCustomActionsProviderFactory extends PeriodicCustomActionsProviderFactory {

  override def create(
      periodicProcessesRepository: PeriodicProcessesRepository,
      service: PeriodicProcessService
  ): PeriodicCustomActionsProvider = new PeriodicCustomActionsProvider with LazyLogging {
    implicit val ec: ExecutionContext = ExecutionContext.global
    import periodicProcessesRepository._

    override def customActions: List[CustomActionDefinition] = List(InstantBatchCustomAction())

    override def invokeCustomAction(actionRequest: DMCustomActionCommand): Future[CustomActionResult] = {
      actionRequest.actionName match {
        case ScenarioActionName.RunNow => actionInstantBatch(actionRequest)
        case _                         => Future.failed(new NotImplementedError())
      }
    }

    private def actionInstantBatch(actionRequest: DMCustomActionCommand): Future[CustomActionResult] = {
      val processName           = actionRequest.processVersion.processName
      val instantScheduleResult = instantSchedule(processName)
      instantScheduleResult
        .map(_ => CustomActionResult(s"Scenario ${processName.value} scheduled for immediate start"))
        .getOrElse(CustomActionResult(s"Failed to schedule $processName to run as instant batch"))
    }

    // TODO: Why we don't allow running not scheduled scenario? Maybe we can try to schedule it?
    private def instantSchedule(processName: ProcessName): OptionT[Future, Unit] = for {
      // schedule for immediate run
      processDeployment <- OptionT(
        service
          .getLatestDeploymentsForActiveSchedules(processName)
          .map(_.groupedByPeriodicProcess.headOption.flatMap(_.deployments.headOption))
      )
      processDeploymentWithProcessJson <- OptionT.liftF(
        periodicProcessesRepository.findProcessData(processDeployment.id).run
      )
      _ <- OptionT.liftF(service.deploy(processDeploymentWithProcessJson))
    } yield ()

  }

}

//TODO: replace custom action with dedicated command in core services
case object InstantBatchCustomAction {

  def apply(): CustomActionDefinition = {
    CustomActionDefinition(
      actionName = RunNow,
      allowedStateStatusNames = List("SCHEDULED"),
      icon = Some(new URI("/assets/custom-actions/batch-instant.svg")),
      parameters = Nil
    )
  }

}
