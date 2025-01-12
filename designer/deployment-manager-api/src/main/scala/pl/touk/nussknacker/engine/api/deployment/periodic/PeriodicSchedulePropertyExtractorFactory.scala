package pl.touk.nussknacker.engine.api.deployment.periodic

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

trait PeriodicSchedulePropertyExtractorFactory {
  def apply(config: Config): PeriodicSchedulePropertyExtractor
}

trait PeriodicSchedulePropertyExtractor {
  def apply(canonicalProcess: CanonicalProcess): Either[String, PeriodicScheduleProperty]
}

sealed trait PeriodicScheduleProperty

sealed trait SinglePeriodicScheduleProperty extends PeriodicScheduleProperty

final case class MultiplePeriodicScheduleProperty(schedules: Map[String, SinglePeriodicScheduleProperty])
    extends PeriodicScheduleProperty

final case class CronPeriodicScheduleProperty(labelOrCronExpr: String) extends SinglePeriodicScheduleProperty
