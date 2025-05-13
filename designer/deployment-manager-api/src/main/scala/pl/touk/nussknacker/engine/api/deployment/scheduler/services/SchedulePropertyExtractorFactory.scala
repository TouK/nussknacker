package pl.touk.nussknacker.engine.api.deployment.scheduler.services

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.ScheduleProperty
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait SchedulePropertyExtractorFactory {
  def apply(config: Config): SchedulePropertyExtractor
}

trait SchedulePropertyExtractor {
  def apply(canonicalProcess: CanonicalProcess): Either[String, ScheduleProperty]
}
