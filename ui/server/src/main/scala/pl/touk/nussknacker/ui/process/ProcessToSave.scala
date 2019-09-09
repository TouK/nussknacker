package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.CirceRestCodecs.displayableDecoder
import pl.touk.nussknacker.restmodel.CirceRestCodecs.displayableEncoder

@JsonCodec case class ProcessToSave(process: DisplayableProcess, comment: String)