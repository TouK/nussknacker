package pl.touk.nussknacker.engine.classloader

import com.typesafe.scalalogging.LazyLogging

import java.net.URL
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class ModelClassLoader private[classloader] (val urls: List[URL], parent: ClassLoader)
    extends URLClassLoader(urls, parent) {

  override def toString: String = s"ModelClassLoader(${toString(this)})"

  private def toString(classLoader: ClassLoader): String = classLoader match {
    case null                => "null"
    case url: URLClassLoader => url.getURLs.mkString("URLClassLoader(List(", ", ", s"), ${toString(url.getParent)})")
    case other               => s"${other.toString}(${toString(other.getParent)})"
  }

}

object ModelClassLoader extends LazyLogging {
  // for e.g. testing in process module
  val empty: ModelClassLoader = new ModelClassLoader(List.empty, getClass.getClassLoader)
  // This is a workaround for a behaviour added in https://issues.apache.org/jira/browse/FLINK-32265
  // We can't pass URLClassLoader with empty classpath, because  Flink overwrite user classloader
  // by the AppClassLoader if classpaths parameter is empty
  // (implementation in org.apache.flink.runtime.execution.librarycache.BlobLibraryCacheManager)
  // which holds all needed jars/classes in case of running from Scala plugin in IDE.
  // but in case of running from sbt it contains only sbt-launcher.jar
  val flinkWorkAroundEmptyClassloader: ModelClassLoader =
    new ModelClassLoader(List(new URL("http://dummy-classpath.invalid")), getClass.getClassLoader)

}
