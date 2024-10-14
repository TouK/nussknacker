package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
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
        case _: ScenarioActivity.ScenarioCreated                    => None
        case _: ScenarioActivity.ScenarioArchived                   => None
        case _: ScenarioActivity.ScenarioUnarchived                 => None
        case activity: ScenarioActivity.ScenarioDeployed            => activity.dateFinished
        case activity: ScenarioActivity.ScenarioPaused              => activity.dateFinished
        case activity: ScenarioActivity.ScenarioCanceled            => activity.dateFinished
        case _: ScenarioActivity.ScenarioModified                   => None
        case _: ScenarioActivity.ScenarioNameChanged                => None
        case _: ScenarioActivity.CommentAdded                       => None
        case _: ScenarioActivity.AttachmentAdded                    => None
        case _: ScenarioActivity.ChangedProcessingMode              => None
        case _: ScenarioActivity.IncomingMigration                  => None
        case _: ScenarioActivity.OutgoingMigration                  => None
        case activity: ScenarioActivity.PerformedSingleExecution    => activity.dateFinished
        case activity: ScenarioActivity.PerformedScheduledExecution => activity.dateFinished
        case _: ScenarioActivity.AutomaticUpdate                    => None
        case activity: ScenarioActivity.CustomAction                => activity.dateFinished
      }
    }

  }

}
