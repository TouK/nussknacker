package pl.touk.nussknacker.engine.util.loader

import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, One}

object ProcessConfigCreatorLoader {
  def loadProcessConfigCreator(classLoader: ClassLoader): ProcessConfigCreator = {
    SingleServiceLoader.load[ProcessConfigCreator](classLoader) match {
      case Empty() =>
        throw new IllegalArgumentException("ProcessConfigCreator not found")
      case Many(muchEntities) =>
        throw new IllegalArgumentException(s"found many ProcessConfigCreatorImplementations: $muchEntities")
      case One(only) => only
    }
  }
}