package pl.touk.nussknacker.engine.util

import java.net.{URL, URLClassLoader}

//https://github.com/apache/flink/blob/master/flink-core/src/main/java/org/apache/flink/core/plugin/PluginLoader.java
class PluginClassloader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {



}
