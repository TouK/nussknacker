package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.model

import com.azure.core.annotation.Fluent
import com.fasterxml.jackson.annotation.JsonProperty

import java.util
import scala.beans.BeanProperty

// Original com.azure.data.schemaregistry.implementation.models.SchemaVersions class has configured invalid field name -
// schemaVersions instead of Values which is returned by API
@Fluent final class SchemaVersions {

  @JsonProperty(value = "Value")
  @BeanProperty
  var schemaVersions: util.List[Integer] = _

}
