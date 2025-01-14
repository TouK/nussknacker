package pl.touk.nussknacker.engine.api.deployment.periodic.services

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicScheduleProperty
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait PeriodicSchedulePropertyExtractorFactory {
  def apply(config: Config): PeriodicSchedulePropertyExtractor
}

trait PeriodicSchedulePropertyExtractor {
  def apply(canonicalProcess: CanonicalProcess): Either[String, PeriodicScheduleProperty]
}
