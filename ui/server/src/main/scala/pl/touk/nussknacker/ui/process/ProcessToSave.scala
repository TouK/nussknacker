package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess

@JsonCodec case class ProcessToSave(process: DisplayableProcess, comment: String)