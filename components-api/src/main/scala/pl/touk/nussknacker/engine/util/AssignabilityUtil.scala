package pl.touk.nussknacker.engine.util

import org.apache.commons.lang3.ClassUtils

object AssignabilityUtil {

  // this makes it possible to avoid having dependency on the library which declares "to" class
  def isAssignableToLoadableClass(from: Class[_], toClassReference: String): Boolean = {
    val loader = Thread.currentThread().getContextClassLoader
    val toOption =
      try {
        Some(loader.loadClass(toClassReference))
      } catch {
        case _: ClassNotFoundException => None
      }
    toOption match {
      case Some(loadedTo) => ClassUtils.isAssignable(from, loadedTo)
      case None           => false
    }
  }

}
