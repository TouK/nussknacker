package pl.touk.nussknacker.ui.process.periodic.cron

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{
  CronPeriodicScheduleProperty,
  MultiplePeriodicScheduleProperty,
  PeriodicScheduleProperty,
  SinglePeriodicScheduleProperty
}
import pl.touk.nussknacker.engine.api.deployment.periodic.services.PeriodicSchedulePropertyExtractor
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.ui.process.periodic.utils.SchedulePropertyExtractorUtils
import pl.touk.nussknacker.ui.process.periodic.{CronScheduleProperty, MultipleScheduleProperty, SingleScheduleProperty}

object CronSchedulePropertyExtractor {
  val CronPropertyDefaultName = "cron"
}

case class CronSchedulePropertyExtractor(propertyName: String = CronPropertyDefaultName)
    extends PeriodicSchedulePropertyExtractor
    with LazyLogging {

  override def apply(canonicalProcess: CanonicalProcess): Either[String, PeriodicScheduleProperty] = {
    SchedulePropertyExtractorUtils.extractProperty(canonicalProcess, propertyName).map {
      case MultipleScheduleProperty(schedules) =>
        MultiplePeriodicScheduleProperty(schedules.map { case (k, v) => (k, toApi(v)) })
      case cronProperty: CronScheduleProperty =>
        toApi(cronProperty)
    }
  }

  private def toApi(singleProperty: SingleScheduleProperty): SinglePeriodicScheduleProperty = {
    singleProperty match {
      case CronScheduleProperty(labelOrCronExpr) => CronPeriodicScheduleProperty(labelOrCronExpr)
    }
  }

}
