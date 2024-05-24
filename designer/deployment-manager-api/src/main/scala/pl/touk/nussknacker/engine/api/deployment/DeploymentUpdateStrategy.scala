package pl.touk.nussknacker.engine.api.deployment

sealed trait DeploymentUpdateStrategy

object DeploymentUpdateStrategy {

  final case class ReplaceDeploymentWithSameScenarioName(stateRestoringStrategy: StateRestoringStrategy)
      extends DeploymentUpdateStrategy

  case object DontReplaceDeployment extends DeploymentUpdateStrategy

  sealed trait StateRestoringStrategy

  object StateRestoringStrategy {

    case object RestoreStateFromReplacedJobSavepoint extends StateRestoringStrategy

    final case class RestoreStateFromCustomSavepoint(savepointPath: String) extends StateRestoringStrategy

  }

}
