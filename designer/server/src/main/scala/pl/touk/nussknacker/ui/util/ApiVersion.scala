package pl.touk.nussknacker.ui.util

import derevo.circe._
import derevo.derive

@derive(encoder, decoder)
final case class ApiVersion(version: Int)
