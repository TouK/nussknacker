package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.time.Clock

trait SchedulePropertyExtractor {
  def apply(graphProcess: GraphProcess): Either[String, ScheduleProperty]
}

object SchedulePropertyExtractor {

  def extractProperty(graphProcess: GraphProcess, name: String): Either[String, String] = {
      for {
        canonicalProcess <- ProcessMarshaller.fromGraphProcess(graphProcess).leftMap(_ => "Scenario is unparseable").toEither.right
        property <- canonicalProcess.metaData.additionalFields.flatMap(_.properties.get(name)).toRight(s"$name property is missing").right
      } yield property
  }

}

object CronSchedulePropertyExtractor {

  val CronPropertyDefaultName = "cron"

}

case class CronSchedulePropertyExtractor(propertyName: String = CronPropertyDefaultName) extends SchedulePropertyExtractor with LazyLogging {

  override def apply(graphProcess: GraphProcess): Either[String, ScheduleProperty] =
    for {
      cronProperty <- SchedulePropertyExtractor.extractProperty(graphProcess, propertyName).right
      cronScheduleProperty <- Right(CronScheduleProperty(cronProperty)).right
      _ <- cronScheduleProperty.nextRunAt(Clock.systemDefaultZone()).right
    } yield cronScheduleProperty

}
