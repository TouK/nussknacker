package pl.touk.esp.engine.api

trait UserDefinedProcessAdditionalFields

case class MetaData(id: String,
                   //TODO: pallelism i splitStateToDisk - stream specific :|
                    parallelism: Option[Int] = None,
                    splitStateToDisk: Option[Boolean] = None,
                    additionalFields: Option[UserDefinedProcessAdditionalFields] = None)
