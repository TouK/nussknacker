package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config

trait SchedulePropertyExtractorFactory {
  def apply(config: Config): SchedulePropertyExtractor
}
