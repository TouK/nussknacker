package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName

import java.time.Clock

trait SchedulePropertyExtractor {
  def apply(canonicalProcess: CanonicalProcess): Either[String, ScheduleProperty]
}

object SchedulePropertyExtractor {

  def extractProperty(canonicalProcess: CanonicalProcess, name: String): Either[String, String] = {
    canonicalProcess.metaData.additionalFields.flatMap(_.properties.get(name)).toRight(s"$name property is missing")
  }

}

object CronSchedulePropertyExtractor {

  val CronPropertyDefaultName = "cron"

}

case class CronSchedulePropertyExtractor(propertyName: String = CronPropertyDefaultName) extends SchedulePropertyExtractor with LazyLogging {

  override def apply(canonicalProcess: CanonicalProcess): Either[String, ScheduleProperty] =
    for {
      cronProperty <- SchedulePropertyExtractor.extractProperty(canonicalProcess, propertyName).right
      cronScheduleProperty <- Right(CronScheduleProperty(cronProperty)).right
      _ <- cronScheduleProperty.nextRunAt(Clock.systemDefaultZone()).right
    } yield cronScheduleProperty

}
