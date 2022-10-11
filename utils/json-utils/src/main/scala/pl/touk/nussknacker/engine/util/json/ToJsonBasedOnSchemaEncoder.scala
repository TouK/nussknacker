package pl.touk.nussknacker.engine.util.json

import cats.data.ValidatedNel
import io.circe.Json
import org.everit.json.schema.Schema

trait ToJsonBasedOnSchemaEncoder {
  type WithError[T] = ValidatedNel[String, T]

  def encoder(delegateEncode: (Any, Schema, Option[String]) => WithError[Json]): PartialFunction[(Any, Schema, Option[String]), WithError[Json]]
}
