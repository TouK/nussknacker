package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.model

import com.azure.core.annotation.Fluent
import com.fasterxml.jackson.annotation.JsonProperty

import java.util
import scala.beans.BeanProperty

@Fluent final class Schemas {

  @JsonProperty(value = "Value")
  @BeanProperty
  var schemas: util.List[String] = _

}
