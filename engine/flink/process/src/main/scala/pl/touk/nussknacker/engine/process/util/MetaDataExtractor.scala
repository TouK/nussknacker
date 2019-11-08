package pl.touk.nussknacker.engine.process.util

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.WrongProcessType

import scala.reflect.ClassTag

object MetaDataExtractor {

  def extractTypeSpecificData[T <: TypeSpecificData](metaData: MetaData)(implicit classTag: ClassTag[T]): Either[NonEmptyList[WrongProcessType], T] = metaData.typeSpecificData match {
    case a: T => Right(a)
    case _ => Left(NonEmptyList.of(WrongProcessType()))
  }

  def extractTypeSpecificDataOrFail[T <: TypeSpecificData](metaData: MetaData)(implicit classTag: ClassTag[T]): T
    = extractTypeSpecificData(metaData).fold(_ => throw new IllegalArgumentException("Wrong process type"), identity)

}
