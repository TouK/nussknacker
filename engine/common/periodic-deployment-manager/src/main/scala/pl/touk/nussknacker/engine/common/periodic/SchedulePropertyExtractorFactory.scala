package pl.touk.nussknacker.engine.common.periodic

import com.typesafe.config.Config

trait SchedulePropertyExtractorFactory {
  def apply(config: Config): SchedulePropertyExtractor
}
