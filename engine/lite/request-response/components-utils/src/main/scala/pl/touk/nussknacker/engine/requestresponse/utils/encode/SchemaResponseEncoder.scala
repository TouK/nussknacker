package pl.touk.nussknacker.engine.requestresponse.utils.encode

import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.encode.ToJsonSchemaBasedEncoder
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder

class SchemaResponseEncoder(schema: Schema, validationMode: ValidationMode) extends ResponseEncoder[Any] {

  private val encoder = new ToJsonSchemaBasedEncoder(validationMode)

  override def toJsonResponse(input: Any, result: List[Any]): Json = {
    result
      .map(encoder.encodeOrError(_, schema))
      .headOption
      .getOrElse(throw new IllegalArgumentException(s"Scenario did not return any result"))
  }

}
