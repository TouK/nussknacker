package pl.touk.nussknacker.engine.util

import cats.data.ValidatedNel
import io.circe.Json
import org.everit.json.schema.Schema

package object json {
  type EncodeInput = (Any, Schema, Schema, Option[String])
  type WithError[T] = ValidatedNel[String, T]
  type EncodeOutput = WithError[Json]
}
