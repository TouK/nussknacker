package pl.touk.nussknacker.engine.util.namespaces

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.namespaces.{DefaultObjectNaming, ObjectNaming}
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

/**
 * Object returns an instance of [[pl.touk.nussknacker.engine.api.namespaces.ObjectNaming]]
 * loaded with Java SPI, if there is one provided, or the default implementation
 * [[pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming]] otherwise.
 */
object ObjectNamingProvider extends LazyLogging with Serializable {
  def apply(classLoader: ClassLoader): ObjectNaming = {
    ScalaServiceLoader.loadClass[ObjectNaming](classLoader) {
      DefaultObjectNaming
    }
  }
}
