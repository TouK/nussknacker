package pl.touk.nussknacker.engine.dict

import pl.touk.nussknacker.engine.api.dict.DictServicesFactory
import pl.touk.nussknacker.engine.util.loader.{LoadClassFromClassLoader, ScalaServiceLoader}

object DictServicesFactoryLoader extends LoadClassFromClassLoader {
  override type ClassToLoad = DictServicesFactory
  override val prettyClassName: String = "DictServicesFactory"

  override def loadAll(classLoader: ClassLoader): List[DictServicesFactory] = {
    List(ScalaServiceLoader.loadClass[DictServicesFactory](classLoader) {
      SimpleDictServicesFactory
    })
  }
}
