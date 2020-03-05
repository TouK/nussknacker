package pl.touk.nussknacker.engine.process.util

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.WrongProcessType
import pl.touk.nussknacker.engine.api.{MetaData, TypeSpecificData}

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
    extractProperty(metaData, property).map(_.toLong).getOrElse(default)
}

