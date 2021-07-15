package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.management.periodic.CronSchedulePropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.time.Clock

trait SchedulePropertyExtractor {
  def apply(processDeploymentData: ProcessDeploymentData): Either[String, ScheduleProperty]
}

object SchedulePropertyExtractor {

  def extractProperty(processDeploymentData: ProcessDeploymentData, name: String): Either[String, String] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        for {
          canonicalProcess <- ProcessMarshaller.fromJson(processAsJson).leftMap(_ => "Process is unparseable").toEither.right
          property <- canonicalProcess.metaData.additionalFields.flatMap(_.properties.get(name)).toRight(s"$name property is missing").right
        } yield property
      case CustomProcess(_) => Left("Custom scenario is not supported")
    }
  }

}

object CronSchedulePropertyExtractor {

  val CronPropertyDefaultName = "cron"

}

case class CronSchedulePropertyExtractor(propertyName: String = CronPropertyDefaultName) extends SchedulePropertyExtractor with LazyLogging {

  override def apply(processDeploymentData: ProcessDeploymentData): Either[String, ScheduleProperty] =
    for {
      cronProperty <- SchedulePropertyExtractor.extractProperty(processDeploymentData, propertyName).right
      cronScheduleProperty <- Right(CronScheduleProperty(cronProperty)).right
      _ <- cronScheduleProperty.nextRunAt(Clock.systemDefaultZone()).right
    } yield cronScheduleProperty

}