package pl.touk.nussknacker.engine.process.util

object UserClassLoader {

  def get(node: String) : ClassLoader = {
    val classLoader = Thread.currentThread().getContextClassLoader
    // FIXME: don't identify classloader by string
    if (!classLoader.getClass.getName.startsWith("org.apache.flink")) {
      throw new IllegalArgumentException("UserClassLoader.get() invoked in wrong context - " +
        s"classloader is not instance of ParentFirstClassLoader or ChildFirstClassLoader, but ${classLoader.getClass.getName}, please check your custom transformer: $node")
    }
    classLoader
  }

}
