package pl.touk.nussknacker.ui.statistics

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

// deploymentManagerType is optional which rather shouldn't happen in normal usage, but we don't want to throw
// an exception if someone has some specific programmatically created setup
final case class ProcessingTypeUsageStatistics(deploymentManagerType: Option[String], processingMode: Option[String])

object ProcessingTypeUsageStatistics {
  // TODO: handle only enabled managers by category configuration
  def apply(managerConfig: Config): ProcessingTypeUsageStatistics =
    ProcessingTypeUsageStatistics(managerConfig.getAs[String]("type"), managerConfig.getAs[String]("mode"))
}
