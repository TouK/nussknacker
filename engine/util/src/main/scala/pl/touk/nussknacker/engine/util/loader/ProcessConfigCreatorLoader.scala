package pl.touk.nussknacker.engine.util.loader

import pl.touk.nussknacker.engine.api.conversion.ProcessConfigCreatorMapping
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

object ProcessConfigCreatorLoader {
  type JPCC = pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator
  type SPCC = ProcessConfigCreator

  def loadProcessConfigCreator(classLoader: ClassLoader): ProcessConfigCreator = {
    Multiplicity(load(classLoader)) match {
      case Empty() =>
        throw new IllegalArgumentException("ProcessConfigCreator not found")
      case Many(muchEntities) =>
        throw new IllegalArgumentException(s"found many ProcessConfigCreatorImplementations: $muchEntities")
      case One(only) => only
    }
  }

  private def load(classLoader: ClassLoader) = {
    ScalaServiceLoader.load[SPCC](classLoader) ++ {
      ScalaServiceLoader.load[JPCC](classLoader)
        .map {
          ProcessConfigCreatorMapping.toProcessConfigCreator
        }
    }
  }
}