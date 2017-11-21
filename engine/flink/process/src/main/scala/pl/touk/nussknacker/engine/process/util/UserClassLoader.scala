package pl.touk.nussknacker.engine.process.util

import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoader

object UserClassLoader {

  def get(node: String) : ClassLoader = {
    val classLoader = Thread.currentThread().getContextClassLoader
    if (!classLoader.isInstanceOf[FlinkUserCodeClassLoader]) {
      throw new IllegalArgumentException("UserClassLoader.get() invoked in wrong context - " +
        s"classloader is not instance of FlinkUserCodeClassLoader, please check your custom transformer: $node")
    }
    classLoader
  }

}
