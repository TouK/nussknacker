package pl.touk.esp.engine.api

trait UserDefinedProcessAdditionalFields

case class MetaData(id: String,
                    parallelism: Option[Int] = None,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None)
