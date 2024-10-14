package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityState, ScheduledExecutionStatus}
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

    def stateOpt: Option[ScenarioActivityState] = {
      scenarioActivity match {
        case _: ScenarioActivity.ScenarioCreated                 => None
        case _: ScenarioActivity.ScenarioArchived                => None
        case _: ScenarioActivity.ScenarioUnarchived              => None
        case activity: ScenarioActivity.ScenarioDeployed         => Some(activity.state)
        case activity: ScenarioActivity.ScenarioPaused           => Some(activity.state)
        case activity: ScenarioActivity.ScenarioCanceled         => Some(activity.state)
        case _: ScenarioActivity.ScenarioModified                => None
        case _: ScenarioActivity.ScenarioNameChanged             => None
        case _: ScenarioActivity.CommentAdded                    => None
        case _: ScenarioActivity.AttachmentAdded                 => None
        case _: ScenarioActivity.ChangedProcessingMode           => None
        case _: ScenarioActivity.IncomingMigration               => None
        case _: ScenarioActivity.OutgoingMigration               => None
        case activity: ScenarioActivity.PerformedSingleExecution => Some(activity.state)
        case activity: ScenarioActivity.PerformedScheduledExecution =>
          Some(stateFrom(activity.scheduledExecutionStatus))
        case _: ScenarioActivity.AutomaticUpdate     => None
        case activity: ScenarioActivity.CustomAction => Some(activity.state)
      }
    }

    private def stateFrom(scheduledExecutionStatus: ScheduledExecutionStatus): ScenarioActivityState =
      scheduledExecutionStatus match {
        case ScheduledExecutionStatus.Scheduled =>
          ScenarioActivityState.InProgress
        case ScheduledExecutionStatus.Deployed =>
          ScenarioActivityState.InProgress
        case ScheduledExecutionStatus.Finished =>
          ScenarioActivityState.Success
        case ScheduledExecutionStatus.Failed =>
          ScenarioActivityState.Failure
        case ScheduledExecutionStatus.DeploymentWillBeRetried =>
          ScenarioActivityState.Failure
        case ScheduledExecutionStatus.DeploymentFailed =>
          ScenarioActivityState.Failure
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
