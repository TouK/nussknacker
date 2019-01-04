package pl.touk.nussknacker.ui.api.helpers

import argonaut.Json
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.codec.UiCodecs

object TestProcessUtil {

  def toDisplayable(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): DisplayableProcess = {
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), processingType)
  }

  def toJson(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json = {
    UiCodecs.displayableProcessCodec.encode(toDisplayable(espProcess, processingType))
  }

}
