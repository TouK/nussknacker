package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

trait ObjectNamingProvider extends LazyLogging  {
  def create(classLoader: ClassLoader): ObjectNaming
}

object ObjectNamingProvider extends ObjectNamingProvider with Serializable {
  def create(classLoader: ClassLoader): ObjectNaming = {
    ScalaServiceLoader.loadClass[ObjectNaming](classLoader) {
      DefaultObjectNaming()
    }
  }
}
