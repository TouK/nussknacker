package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config

trait SchedulePropertyExtractorFactory {
  def apply(config: Config): SchedulePropertyExtractor
}
