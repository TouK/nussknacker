package pl.touk.nussknacker.ui.util

import derevo.circe._
import derevo.derive

//TODO: Try with @derive(schema) as well
@derive(encoder, decoder)
final case class ApiVersion(version: Int)
