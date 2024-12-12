package pl.touk.nussknacker.engine.util.loader

import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

class ProcessConfigCreatorLoader(shouldIncludeConfigCreator: ProcessConfigCreator => Boolean)
    extends LoadClassFromClassLoader {

  type JPCC = pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator
  type SPCC = ProcessConfigCreator

  override type ClassToLoad = SPCC
  override val prettyClassName: String = "ProcessConfigCreator"

  override def loadAll(classLoader: ClassLoader): List[SPCC] = {
    // todo:
    ScalaServiceLoader.load[SPCC](classLoader).filter(shouldIncludeConfigCreator) ++
      ScalaServiceLoader
        .load[JPCC](classLoader)
        .map(ProcessConfigCreatorMapping.toProcessConfigCreator)
  }

}
