package pl.touk.nussknacker.ui.statistics

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

case class ProcessingTypeUsageStatistics(deploymentManagerType: String, processingMode: Option[String])

object ProcessingTypeUsageStatistics {
  // TODO: handle only enabled managers by category configuration
  def apply(managerConfig: Config): ProcessingTypeUsageStatistics =
    ProcessingTypeUsageStatistics(
      managerConfig.getString("type"),
      managerConfig.getAs[String]("mode"))
}
