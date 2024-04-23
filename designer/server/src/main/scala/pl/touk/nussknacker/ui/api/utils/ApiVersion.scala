package pl.touk.nussknacker.ui.api.utils

import derevo.circe._
import derevo.derive
import sttp.tapir.derevo.schema

//TODO: Try with @derive(schema) as well
@derive(encoder, decoder, schema)
final case class ApiVersion(version: Int)
