package pl.touk.nussknacker.engine.api.deployment

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec
import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant

// Currently DeploymentStatus are limited set of allowed statuses. Only ProblemDeploymentStatus can have different
// descriptions depending on DM implementation. It makes implementation of logic based on statuses easier. In case
// if we have requirement to make it more flexible, we can relax this restriction.
sealed trait DeploymentStatus
final case class ProblemDeploymentStatus(problemDescription: String) extends DeploymentStatus

object DeploymentStatus {

  object Problem {

    private val DefaultDescription = "There are some problems with deployment."

    val Failed: ProblemDeploymentStatus = ProblemDeploymentStatus(DefaultDescription)

    val FailureDuringDeploymentRequesting: ProblemDeploymentStatus = ProblemDeploymentStatus(
      "There were some problems with deployment requesting"
    )

  }

  final case class DuringDeploy(versionId: VersionId)                extends DeploymentStatus
  final case class Running(versionId: VersionId, startedAt: Instant) extends DeploymentStatus
  final case class Finished(version: VersionId)                      extends DeploymentStatus
  case object Restarting                                             extends DeploymentStatus
  case object DuringCancel                                           extends DeploymentStatus
  case object Canceled                                               extends DeploymentStatus

  implicit class IsActive(val status: DeploymentStatus) extends AnyVal {

    def isActive: Boolean = {
      status match {
        case DuringDeploy(_) | Running(_, _) | Restarting                       => true
        case Finished(_) | DuringCancel | Canceled | ProblemDeploymentStatus(_) => false
      }
    }

  }

  implicit class ProblemDescription(val status: DeploymentStatus) extends AnyVal {

    def problemDescription: Option[String] = status match {
      case Finished(_)                                 => None
      case DuringDeploy(_)                             => None
      case Running(_, _)                               => None
      case Canceled                                    => None
      case DuringCancel                                => None
      case Restarting                                  => None
      case ProblemDeploymentStatus(problemDescription) => Some(problemDescription)
    }

  }

  implicit class ScenarioVersion(val status: DeploymentStatus) extends AnyVal {

    def version: Option[VersionId] = status match {
      case Finished(version)          => Some(version)
      case DuringDeploy(version)      => Some(version)
      case Running(version, _)        => Some(version)
      case Canceled                   => None
      case DuringCancel               => None
      case Restarting                 => None
      case ProblemDeploymentStatus(_) => None
    }

  }

  implicit class StartedAt(val status: DeploymentStatus) extends AnyVal {

    def startedAt: Option[Instant] = status match {
      case Finished(_)                 => None
      case DuringDeploy(_)             => None
      case Running(version, startedAt) => Some(startedAt)
      case Canceled                    => None
      case DuringCancel                => None
      case Restarting                  => None
      case ProblemDeploymentStatus(_)  => None
    }

  }

  implicit class ToDeploymentStatusName(val status: DeploymentStatus) extends AnyVal {

    def name: DeploymentStatusName = status match {
      case DuringDeploy(_)            => DeploymentStatusName.duringDeployStatusName
      case Running(_, _)              => DeploymentStatusName.runningStatusName
      case Finished(_)                => DeploymentStatusName.finishedStatusName
      case Restarting                 => DeploymentStatusName.restartingStatusName
      case DuringCancel               => DeploymentStatusName.duringCancelStatusName
      case Canceled                   => DeploymentStatusName.canceledStatusName
      case ProblemDeploymentStatus(_) => DeploymentStatusName.problemStatusName
    }

  }

}

final case class DeploymentStatusName(value: String) {
  override def toString: String = value
}

object DeploymentStatusName {

  implicit val codec: Codec[DeploymentStatusName] = deriveUnwrappedCodec[DeploymentStatusName]

  val canceledStatusName: DeploymentStatusName     = DeploymentStatusName("CANCELED")
  val duringCancelStatusName: DeploymentStatusName = DeploymentStatusName("DURING_CANCEL")
  val duringDeployStatusName: DeploymentStatusName = DeploymentStatusName("DURING_DEPLOY")
  val finishedStatusName: DeploymentStatusName     = DeploymentStatusName("FINISHED")
  val problemStatusName: DeploymentStatusName      = DeploymentStatusName("PROBLEM")
  val restartingStatusName: DeploymentStatusName   = DeploymentStatusName("RESTARTING")
  val runningStatusName: DeploymentStatusName      = DeploymentStatusName("RUNNING")
}
