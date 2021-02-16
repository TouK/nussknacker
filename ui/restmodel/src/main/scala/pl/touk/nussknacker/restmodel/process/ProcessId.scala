package pl.touk.nussknacker.restmodel.process

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.process.ProcessName

object ProcessId {
  implicit val ProcessIdEncoder: Encoder[ProcessId] = deriveUnwrappedEncoder
  implicit val ProcessIdDecoder: Decoder[ProcessId] = deriveUnwrappedDecoder
}

final case class ProcessId(value: Long) extends AnyVal

final case class ProcessIdWithName(id: ProcessId, name: ProcessName)

final case class ProcessIdWithNameAndCategory(id: ProcessId, name: ProcessName, category: String)
