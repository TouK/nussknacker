package pl.touk.nussknacker.ui.process.periodic.flink.db

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
import pl.touk.nussknacker.ui.process.periodic.SchedulingScenarioActivitiesRepository

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class InMemScenarioActivityRepository extends SchedulingScenarioActivitiesRepository {

  private val activities: mutable.ListBuffer[ScenarioActivity] = ListBuffer.empty

  def getActivities: List[ScenarioActivity] = synchronized {
    activities.toList
  }

  override def add(
      activity: ScenarioActivity.PerformedScheduledExecution
  )(implicit ec: ExecutionContext): Future[Unit] = {
    activities += activity
    Future.unit
  }

}
