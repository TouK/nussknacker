package pl.touk.nussknacker.engine.process.util

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.WrongProcessType

object MetaDataExtractor {

  def extractStreamMetaData(metaData: MetaData) = metaData.typeSpecificData match {
    case a:StreamMetaData => Right(a)
    case _ => Left(NonEmptyList.of(WrongProcessType()))
  }

  def extractStreamMetaDataOrFail(metaData: MetaData)
    = extractStreamMetaData(metaData).fold(_ => throw new IllegalArgumentException("Wrong process type"), identity)

}
