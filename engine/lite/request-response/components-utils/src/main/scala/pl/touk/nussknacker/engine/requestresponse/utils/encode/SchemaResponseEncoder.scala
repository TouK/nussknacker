package pl.touk.nussknacker.engine.requestresponse.utils.encode

import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.encode.BestEffortJsonSchemaEncoder
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder

class SchemaResponseEncoder(schema: Schema) extends ResponseEncoder[Any] {

  private val bestEffortEncoder = new BestEffortJsonSchemaEncoder

  override def toJsonResponse(input: Any, result: List[Any]): Json = {
    result
      .map(bestEffortEncoder.encodeOrError(_, schema))
      .headOption
      .getOrElse(throw new IllegalArgumentException(s"Scenario did not return any result"))
  }

}
