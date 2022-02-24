package pl.touk.nussknacker.engine.util

object ThreadUtils {

  def loadUsingContextLoader(className: String): Class[_] = Thread.currentThread().getContextClassLoader.loadClass(className)

  def withThisAsContextClassLoader[T](classLoader: ClassLoader)(block: => T): T = {
    val currentLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(classLoader)
    try {
      block
    } finally {
      Thread.currentThread().setContextClassLoader(currentLoader)
    }
  }

}
