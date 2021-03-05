package pl.touk.nussknacker.engine.management.periodic.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.management.periodic.definition.CronPropertyExtractor.CronPropertyDefaultName
import pl.touk.nussknacker.engine.management.periodic.definition.PeriodicPropertyExtractor.defaultPeriodicPropertyName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.time.ZonedDateTime

trait PeriodicPropertyExtractor {
  def apply(processDeploymentData: ProcessDeploymentData): Either[String, Map[String, PeriodicProperty]]
}

object PeriodicPropertyExtractor {

  val defaultPeriodicPropertyName = "default"

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

  override def apply(processDeploymentData: ProcessDeploymentData): Either[String, Map[String, PeriodicProperty]] =
    for {
      cronProperty <- PeriodicPropertyExtractor.extractProperty(processDeploymentData, propertyName).right
      cronPeriodicProperty <- Right(CronPeriodicProperty(cronProperty)).right
      //Just to check if it works?
      _ <- cronPeriodicProperty.nextRunAt(ZonedDateTime.now()).right
    } yield Map(defaultPeriodicPropertyName -> cronPeriodicProperty)

}
