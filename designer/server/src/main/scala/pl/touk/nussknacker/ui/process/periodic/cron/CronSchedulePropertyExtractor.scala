package pl.touk.nussknacker.ui.process.periodic.cron

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{ScheduleProperty => ApiScheduleProperty}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.SchedulePropertyExtractor
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic.cron.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.ui.process.periodic.utils.SchedulePropertyExtractorUtils
import pl.touk.nussknacker.ui.process.periodic.{CronScheduleProperty, MultipleScheduleProperty, SingleScheduleProperty}

object CronSchedulePropertyExtractor {
  val CronPropertyDefaultName = "cron"
}

case class CronSchedulePropertyExtractor(propertyName: String = CronPropertyDefaultName)
    extends SchedulePropertyExtractor
    with LazyLogging {

  override def apply(canonicalProcess: CanonicalProcess): Either[String, ApiScheduleProperty] = {
    SchedulePropertyExtractorUtils.extractProperty(canonicalProcess, propertyName).map {
      case MultipleScheduleProperty(schedules) =>
        ApiScheduleProperty.MultipleScheduleProperty(schedules.map { case (k, v) => (k, toApi(v)) })
      case cronProperty: CronScheduleProperty =>
        toApi(cronProperty)
    }
  }

  private def toApi(singleProperty: SingleScheduleProperty): ApiScheduleProperty.SingleScheduleProperty = {
    singleProperty match {
      case CronScheduleProperty(labelOrCronExpr) => ApiScheduleProperty.CronScheduleProperty(labelOrCronExpr)
    }
  }

}
