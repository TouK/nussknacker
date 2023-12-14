package pl.touk.nussknacker.engine.process.helpers

import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies}

class ConfigCreatorWithListener(collectingListener: ProcessListener) extends EmptyProcessConfigCreator {

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    Seq(collectingListener)

}
