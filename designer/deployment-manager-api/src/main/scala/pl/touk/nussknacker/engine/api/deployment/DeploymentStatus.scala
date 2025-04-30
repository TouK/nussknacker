package pl.touk.nussknacker.engine.api.deployment

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.UpperSnakecase
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

// Currently DeploymentStatus are limited set of allowed statuses. Only ProblemDeploymentStatus can have different
// descriptions depending on DM implementation. It makes implementation of logic based on statuses easier. In case
// if we have requirement to make it more flexible, we can relax this restriction.
sealed trait DeploymentStatus extends EnumEntry with UpperSnakecase {
  def name: DeploymentStatusName
  def description: Option[String]
}

sealed abstract class NoAttributesDeploymentStatus extends DeploymentStatus {
  override val name: DeploymentStatusName  = DeploymentStatusName(entryName)
  override val description: Option[String] = None
}

final case class ProblemDeploymentStatus(problemDescription: String) extends DeploymentStatus {
  override val name: DeploymentStatusName  = ProblemDeploymentStatus.name
  override val description: Option[String] = Some(problemDescription)
}

object ProblemDeploymentStatus {
  val name: DeploymentStatusName = DeploymentStatusName("PROBLEM")
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

  implicit class IsActive(val status: DeploymentStatus) extends AnyVal {

    def isActive: Boolean = {
      status match {
        case DuringDeploy | Running | Restarting                             => true
        case Finished | DuringCancel | Canceled | ProblemDeploymentStatus(_) => false
      }
    }

  }

  def from(name: DeploymentStatusName, description: Option[String]): DeploymentStatus = {
    name match {
      case ProblemDeploymentStatus.name =>
        val desc = description.getOrElse(throw new IllegalStateException("No description for ProblemDeploymentStatus"))
        ProblemDeploymentStatus(desc)
      case other =>
        DeploymentStatus.withName(other.value)
    }
  }

}

final case class DeploymentStatusName(value: String) {
  override def toString: String = value
}

object DeploymentStatusName {

  implicit val codec: Codec[DeploymentStatusName] = deriveUnwrappedCodec[DeploymentStatusName]

}
