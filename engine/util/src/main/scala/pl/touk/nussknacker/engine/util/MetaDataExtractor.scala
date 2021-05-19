package pl.touk.nussknacker.engine.util

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.WrongProcessType
import pl.touk.nussknacker.engine.api.{MetaData, TypeSpecificData}

import java.time.format.DateTimeFormatter
import java.time._
import scala.reflect.ClassTag

object MetaDataExtractor {

  def extractTypeSpecificData[T <: TypeSpecificData](metaData: MetaData)(implicit classTag: ClassTag[T]): Either[NonEmptyList[WrongProcessType], T] = metaData.typeSpecificData match {
    case a: T => Right(a)
    case _ => Left(NonEmptyList.of(WrongProcessType()))
  }

  def extractTypeSpecificDataOrFail[T <: TypeSpecificData](metaData: MetaData)(implicit classTag: ClassTag[T]): T
  = extractTypeSpecificData(metaData).fold(_ => throw new IllegalArgumentException("Wrong process type"), identity)

  def extractProperty(metaData: MetaData, property: String): Option[String] =
    metaData
      .additionalFields
      .flatMap(
        _.properties
          .get(property)
      )

  def extractProperty(metaData: MetaData, property: String, default: String): String =
    extractProperty(metaData, property).getOrElse(default)

  def extractBooleanProperty(metaData: MetaData, property: String): Option[Boolean] =
    extractProperty(metaData, property).map(_.toBoolean)

  def extractBooleanProperty(metaData: MetaData, property: String, default: Boolean): Boolean =
    extractProperty(metaData, property).map(_.toBoolean).getOrElse(default)

  def extractLongProperty(metaData: MetaData, property: String): Option[Long] =
    extractProperty(metaData, property).map(_.toLong)

  def extractLongProperty(metaData: MetaData, property: String, default: Long): Long =
    extractProperty(metaData, property).map(str => str.toLong).getOrElse(default)

  def extractDateTimeProperty(metaData: MetaData, property: String, default: LocalDateTime): LocalDateTime =
    extractProperty(metaData, property).map(java.time.LocalDateTime.parse(_, DateTimeFormatter.ISO_LOCAL_DATE_TIME)).getOrElse(default)

  def extractTimeProperty(metaData: MetaData, property: String, default: LocalTime): LocalTime =
    extractProperty(metaData, property).map(java.time.LocalTime.parse(_, DateTimeFormatter.ISO_LOCAL_TIME)).getOrElse(default)

  def extractDateProperty(metaData: MetaData, property: String, default: LocalDate): LocalDate =
    extractProperty(metaData, property).map(java.time.LocalDate.parse(_, DateTimeFormatter.ISO_DATE)).getOrElse(default)

  def extractDurationProperty(metaData: MetaData, property: String, default: Duration): Duration =
    extractProperty(metaData, property).map(Duration.parse(_)).getOrElse(default)

  def extractPeriodProperty(metaData: MetaData, property: String, default: Period): Period =
    extractProperty(metaData, property).map(Period.parse(_)).getOrElse(default)
}
