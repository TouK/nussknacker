package pl.touk.nussknacker.engine.requestresponse

import io.circe.Json
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.requestresponse.api.ResponseEncoder
import pl.touk.nussknacker.engine.schemedkafka.encode.{BestEffortJsonSchemaEncoder, ValidationMode}

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  private val bestEffortEncoder = new BestEffortJsonSchemaEncoder(ValidationMode.lax)

  override def toJsonResponse(input: Any, result: List[Any], schema: Option[Schema]): Json = {
    result
      .map(bestEffortEncoder.encodeOrError(_, schema.get))
      .headOption
      .getOrElse(throw new IllegalArgumentException(s"Process did not return any result"))
  }

}
