package pl.touk.nussknacker.ui.process.periodic

import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionId, ScenarioActivity, ScenarioActivityId}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.ActionService
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository

import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}

final class SchedulingDependencies(
    val dbRef: DbRef,
    val actionService: ProcessingTypeActionService,
    val fetchingProcessRepository: FetchingProcessRepository[Future],
    val schedulingScenarioActivitiesRepository: SchedulingScenarioActivitiesRepository,
    val configsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
    val periodicLock: PeriodicLock,
)

trait SchedulingScenarioActivitiesRepository {

  def add(activity: ScenarioActivity.PerformedScheduledExecution)(implicit ec: ExecutionContext): Future[Unit]

}

class DefaultSchedulingScenarioActivitiesRepository(
    activitiesRepository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner
) extends SchedulingScenarioActivitiesRepository {

  override def add(activity: ScenarioActivity.PerformedScheduledExecution)(
      implicit ec: ExecutionContext
  ): Future[Unit] =
    dbioActionRunner
      .run(activitiesRepository.addActivity(activity))
      .map((_: ScenarioActivityId) => ())

}

trait ProcessingTypeActionService {

  // Marks action execution finished. Returns true if update has some effect
  def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean]

}

class DefaultProcessingTypeActionService(
    processingType: ProcessingType,
    actionServiceSupplier: Supplier[ActionService],
) extends ProcessingTypeActionService {

  override def markActionExecutionFinished(actionId: ProcessActionId)(implicit ec: ExecutionContext): Future[Boolean] =
    actionServiceSupplier.get().markActionExecutionFinished(processingType, actionId)

}

// This stub is in API module because we don't want to extract deployment-manager-tests-utils module
class ProcessingTypeActionServiceStub extends ProcessingTypeActionService {

  @volatile
  var actionIds: List[ProcessActionId] = List.empty

  override def markActionExecutionFinished(
      actionId: ProcessActionId
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    actionIds = actionId :: actionIds
    Future.successful(true)
  }

  def sentActionIds: List[ProcessActionId] = actionIds

}
