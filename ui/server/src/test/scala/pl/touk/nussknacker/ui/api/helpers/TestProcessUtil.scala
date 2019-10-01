package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

object TestProcessUtil {

  def toDisplayable(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): DisplayableProcess = {
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), processingType)
  }

  def toJson(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json = {
    Encoder[DisplayableProcess].apply(toDisplayable(espProcess, processingType))
  }

}
