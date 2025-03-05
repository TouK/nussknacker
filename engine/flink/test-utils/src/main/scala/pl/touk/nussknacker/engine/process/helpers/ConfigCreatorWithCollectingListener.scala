package pl.touk.nussknacker.engine.process.helpers

import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener

class ConfigCreatorWithCollectingListener(collectingListener: ResultsCollectingListener[Any])
    extends EmptyProcessConfigCreator {

  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    Seq(collectingListener)

}
