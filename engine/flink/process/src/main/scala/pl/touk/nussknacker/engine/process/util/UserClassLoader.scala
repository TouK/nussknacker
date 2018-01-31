package pl.touk.nussknacker.engine.process.util

object UserClassLoader {

  def get(node: String) : ClassLoader = {
    val classLoader = Thread.currentThread().getContextClassLoader
    // FIXME: don't identify classloader by string
    if (!classLoader.getClass.getName.endsWith("ParentFirstClassLoader") && !classLoader.getClass.getName.endsWith("ChildFirstClassLoader")) {
      throw new IllegalArgumentException("UserClassLoader.get() invoked in wrong context - " +
        s"classloader is not instance of ParentFirstClassLoader or ChildFirstClassLoader, please check your custom transformer: $node")
    }
    classLoader
  }

}
