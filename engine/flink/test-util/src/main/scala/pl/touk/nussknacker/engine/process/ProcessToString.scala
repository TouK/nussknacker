package pl.touk.nussknacker.engine.process

import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.api.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

object ProcessToString {

  def marshall(process: EspProcess): String = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2

}
