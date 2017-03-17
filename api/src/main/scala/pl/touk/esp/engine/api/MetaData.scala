package pl.touk.esp.engine.api

trait UserDefinedProcessAdditionalFields

case class MetaData(id: String,
                    typeSpecificData: TypeSpecificData,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None)

sealed trait TypeSpecificData

case class StreamMetaData(parallelism: Option[Int] = None,
                      splitStateToDisk: Option[Boolean] = None) extends TypeSpecificData

case class StandaloneMetaData(path: Option[String]) extends TypeSpecificData
