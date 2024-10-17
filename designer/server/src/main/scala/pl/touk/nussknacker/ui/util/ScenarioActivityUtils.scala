package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.api.deployment.{DeploymentRelatedActivity, ScenarioActivity}
import pl.touk.nussknacker.ui.db.entity.ScenarioActivityType

import java.time.Instant

object ScenarioActivityUtils {

  implicit class ScenarioActivityOps(scenarioActivity: ScenarioActivity) {

    def activityType: ScenarioActivityType = {
      scenarioActivity match {
        case _: ScenarioActivity.ScenarioCreated             => ScenarioActivityType.ScenarioCreated
        case _: ScenarioActivity.ScenarioArchived            => ScenarioActivityType.ScenarioArchived
        case _: ScenarioActivity.ScenarioUnarchived          => ScenarioActivityType.ScenarioUnarchived
        case _: ScenarioActivity.ScenarioDeployed            => ScenarioActivityType.ScenarioDeployed
        case _: ScenarioActivity.ScenarioPaused              => ScenarioActivityType.ScenarioPaused
        case _: ScenarioActivity.ScenarioCanceled            => ScenarioActivityType.ScenarioCanceled
        case _: ScenarioActivity.ScenarioModified            => ScenarioActivityType.ScenarioModified
        case _: ScenarioActivity.ScenarioNameChanged         => ScenarioActivityType.ScenarioNameChanged
        case _: ScenarioActivity.CommentAdded                => ScenarioActivityType.CommentAdded
        case _: ScenarioActivity.AttachmentAdded             => ScenarioActivityType.AttachmentAdded
        case _: ScenarioActivity.ChangedProcessingMode       => ScenarioActivityType.ChangedProcessingMode
        case _: ScenarioActivity.IncomingMigration           => ScenarioActivityType.IncomingMigration
        case _: ScenarioActivity.OutgoingMigration           => ScenarioActivityType.OutgoingMigration
        case _: ScenarioActivity.PerformedSingleExecution    => ScenarioActivityType.PerformedSingleExecution
        case _: ScenarioActivity.PerformedScheduledExecution => ScenarioActivityType.PerformedScheduledExecution
        case _: ScenarioActivity.AutomaticUpdate             => ScenarioActivityType.AutomaticUpdate
        case activity: ScenarioActivity.CustomAction         => ScenarioActivityType.CustomAction(activity.actionName)
      }
    }

    def dateFinishedOpt: Option[Instant] = {
      scenarioActivity match {
        case activity: DeploymentRelatedActivity => Some(activity.result.dateFinished)
        case _                                   => None
      }
    }

  }

}
