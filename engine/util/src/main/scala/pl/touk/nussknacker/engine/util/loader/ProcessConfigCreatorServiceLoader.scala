package pl.touk.nussknacker.engine.util.loader

import java.util.ServiceLoader
import scala.collection.JavaConverters._

import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator

object ProcessConfigCreatorServiceLoader {

  def createProcessConfigCreator(classLoader: ClassLoader): ProcessConfigCreator = {
    val processConfigCreatorLoader: ServiceLoader[ProcessConfigCreator] = ServiceLoader
      .load(classOf[ProcessConfigCreator], classLoader)
    processConfigCreatorLoader.iterator()
      .asScala
      .toList match {
      case processConfigCreator :: Nil => processConfigCreator
      case Nil => throw ProcessConfigCreatorImplementationNotFound()
      case manyImplementations => throw ProcessConfigCreatorManyImplementationsFound(manyImplementations)
    }
  }

  case class ProcessConfigCreatorImplementationNotFound()
    extends RuntimeException("ServiceLoader couldn't create ProcessConfigCreator")

  case class ProcessConfigCreatorManyImplementationsFound(implementations: List[ProcessConfigCreator])
    extends RuntimeException(s"ServiceLoader found many ProcessConfigCreator. Found implementations: $implementations")

}
