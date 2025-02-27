package pl.touk.nussknacker.ui.process.periodic.utils

import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.process.periodic.{
  CronScheduleProperty,
  MultipleScheduleProperty,
  ScheduleProperty,
  SingleScheduleProperty
}

import java.time.Clock

object SchedulePropertyExtractorUtils {

  def extractProperty(canonicalProcess: CanonicalProcess, name: String): Either[String, ScheduleProperty] = {
    for {
      existingPropertyValue <- canonicalProcess.metaData.additionalFields.properties
        .get(name)
        .toRight(s"$name property is missing")
      parsedValidProperty <- parseAndValidateProperty(existingPropertyValue)
    } yield parsedValidProperty
  }

  private[periodic] def parseAndValidateProperty(stringValue: String): Either[String, ScheduleProperty] =
    for {
      parsedProperty <- parseScheduleProperty(stringValue)
      _ <- parsedProperty match {
        case single: SingleScheduleProperty => single.nextRunAt(Clock.systemDefaultZone())
        case multiple: MultipleScheduleProperty =>
          multiple.schedules.values.toList.map(_.nextRunAt(Clock.systemDefaultZone())).sequence
      }
    } yield parsedProperty

  private def parseScheduleProperty(stringValue: String): Either[String, ScheduleProperty] = {
    val trimmedStringValue = stringValue.trim
    if (trimmedStringValue.startsWith("{") && trimmedStringValue.endsWith("}")) {
      parseMultipleSchedulesExpression(trimmedStringValue)
    } else {
      Right(CronScheduleProperty(stringValue))
    }
  }

  private def parseMultipleSchedulesExpression(trimmedStringValue: String) = {
    val withoutBraces = trimmedStringValue.substring(1, trimmedStringValue.length - 1)
    withoutBraces
      .split(",", -1)
      .filterNot(_.isBlank)
      .map { entry =>
        val entryParts = entry.split(":").toList.map(_.trim)
        entryParts match {
          case scheduleName :: cronExpression :: Nil =>
            Right(
              unwrapPotentialSpringExpression(scheduleName) -> CronScheduleProperty(
                unwrapPotentialSpringExpression(cronExpression)
              )
            )
          case other =>
            Left(s"Schedule property with invalid entry format: $other. Should be scheduleName: 'cron expression'")
        }
      }
      .toList
      .sequence
      .map(_.toMap)
      .map(MultipleScheduleProperty(_))
  }

  private def unwrapPotentialSpringExpression(potentialStringExpression: String): String = {
    if (potentialStringExpression.startsWith("'") && potentialStringExpression.endsWith("'")) {
      potentialStringExpression.substring(1, potentialStringExpression.length - 1)
    } else {
      potentialStringExpression
    }
  }

}
