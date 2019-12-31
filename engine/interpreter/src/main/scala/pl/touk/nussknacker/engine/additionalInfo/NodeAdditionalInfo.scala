package pl.touk.nussknacker.engine.additionalInfo

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

@ConfiguredJsonCodec sealed trait NodeAdditionalInfo

case class MarkdownAdditionalInfo(content: String) extends NodeAdditionalInfo