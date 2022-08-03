package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class BestEffortJsonSchemaEncoder(validationMode: ValidationMode) {

  {
    require(validationMode == ValidationMode.lax, s"${validationMode.name} not supported in BestEffortJsonSchemaEncoder")
  }

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, this.getClass.getClassLoader)


  type WithError[T] = ValidatedNel[String, T]

  final def encodeOrError(value: Any, schema: Schema): AnyRef = {
    encode(value, schema).valueOr(l => throw new RuntimeException(l.toList.mkString(",")))
  }

  //todo: support schema-based encoding and respect validationMode
  def encode(value: Any, schema: Schema, fieldName: Option[String] = None): WithError[AnyRef] = {
    Valid(jsonEncoder.encode(value))
  }
}
