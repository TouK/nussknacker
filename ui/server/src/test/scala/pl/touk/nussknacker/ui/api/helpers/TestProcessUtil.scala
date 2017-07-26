package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

object TestProcessUtil {

  def toDisplayable(espProcess: EspProcess, processingType: ProcessingType = ProcessingType.Streaming): DisplayableProcess = {
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), processingType)
  }

}
