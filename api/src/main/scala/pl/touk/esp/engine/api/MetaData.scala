package pl.touk.esp.engine.api

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration

trait UserDefinedProcessAdditionalFields

case class MetaData(id: String,
                    typeSpecificData: TypeSpecificData,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None)

sealed trait TypeSpecificData

case class StreamMetaData(parallelism: Option[Int] = None,
                          splitStateToDisk: Option[Boolean] = None,
                          checkpointIntervalInSeconds: Option[Long] = None) extends TypeSpecificData {
  def checkpointIntervalDuration = checkpointIntervalInSeconds.map(Duration.apply(_, TimeUnit.SECONDS))
}

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData
