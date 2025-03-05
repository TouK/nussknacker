package pl.touk.nussknacker.engine.api.deployment

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

// Currently DeploymentStatus are limited set of allowed statuses. Only ProblemDeploymentStatus can have different
// descriptions depending on DM implementation. It makes implementation of logic based on statuses easier. In case
// if we have requirement to make it more flexible, we can relax this restriction.
sealed trait DeploymentStatus extends EnumEntry with UpperSnakecase {
  def name: DeploymentStatusName = DeploymentStatusName(entryName)
}

sealed abstract class NoAttributesDeploymentStatus extends DeploymentStatus

final case class ProblemDeploymentStatus(description: String) extends DeploymentStatus {
  override def name: DeploymentStatusName = ProblemDeploymentStatus.name
}

object DeploymentStatus extends Enum[DeploymentStatus] {

  override def values = findValues

  object Problem {

    private val DefaultDescription = "There are some problems with deployment."

    val Failed: ProblemDeploymentStatus = ProblemDeploymentStatus(DefaultDescription)

    val FailureDuringDeploymentRequesting: ProblemDeploymentStatus = ProblemDeploymentStatus(
      "There were some problems with deployment requesting"
    )

  }

  case object DuringDeploy extends NoAttributesDeploymentStatus
  case object Running      extends NoAttributesDeploymentStatus
  case object Finished     extends NoAttributesDeploymentStatus
  case object Restarting   extends NoAttributesDeploymentStatus
  case object DuringCancel extends NoAttributesDeploymentStatus
  case object Canceled     extends NoAttributesDeploymentStatus

}

object ProblemDeploymentStatus {
  def name: DeploymentStatusName = DeploymentStatusName("PROBLEM")

  def extractDescription(status: DeploymentStatus): Option[String] =
    status match {
      case problem: ProblemDeploymentStatus =>
        Some(problem.description)
      case _: NoAttributesDeploymentStatus =>
        None
    }

}

final case class DeploymentStatusName(value: String) {
  override def toString: String = value
}

object DeploymentStatusName {

  implicit val codec: Codec[DeploymentStatusName] = deriveUnwrappedCodec[DeploymentStatusName]

}
