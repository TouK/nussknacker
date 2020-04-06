package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object ObjectNamingProvider extends LazyLogging {
  def apply(classLoader: ClassLoader): ObjectNaming = {
    ScalaServiceLoader.loadClass[ObjectNaming](classLoader) {
      DefaultObjectNaming()
    }
  }
}
