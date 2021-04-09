package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.management.periodic.CronPropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.time.Clock

trait PeriodicPropertyExtractor {
  def apply(processDeploymentData: ProcessDeploymentData): Either[String, PeriodicProperty]
}

object PeriodicPropertyExtractor {

  def extractProperty(processDeploymentData: ProcessDeploymentData, name: String): Either[String, String] = {
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        for {
          canonicalProcess <- ProcessMarshaller.fromJson(processAsJson).leftMap(_ => "Process is unparseable").toEither.right
          property <- canonicalProcess.metaData.additionalFields.flatMap(_.properties.get(name)).toRight(s"$name property is missing").right
        } yield property
      case CustomProcess(_) => Left("Custom process is not supported")
    }
  }

}

object CronPropertyExtractor {

  val CronPropertyDefaultName = "cron"

}

case class CronPropertyExtractor(propertyName: String = CronPropertyDefaultName) extends PeriodicPropertyExtractor with LazyLogging {

  override def apply(processDeploymentData: ProcessDeploymentData): Either[String, PeriodicProperty] =
    for {
      cronProperty <- PeriodicPropertyExtractor.extractProperty(processDeploymentData, propertyName).right
      cronPeriodicProperty <- Right(CronPeriodicProperty(cronProperty)).right
      _ <- cronPeriodicProperty.nextRunAt(Clock.systemDefaultZone()).right
    } yield cronPeriodicProperty

}