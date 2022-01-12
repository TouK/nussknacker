package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging

import java.net.{URL, URLClassLoader}

case class ModelClassLoader private(classLoader: ClassLoader,
                                    urls: List[URL]) {

  override def toString: String = s"ModelClassLoader(${toString(classLoader)})"

  private def toString(classLoader: ClassLoader): String = classLoader match {
    case null => "null"
    case url: URLClassLoader => url.getURLs.mkString("URLClassLoader(List(", ", ", s"), ${toString(url.getParent)})")
    case other => s"${other.toString}(${toString(other.getParent)})"
  }

}

object ModelClassLoader extends LazyLogging {
  //for e.g. testing in process module
  val empty = ModelClassLoader(getClass.getClassLoader, List())

  def apply(urls: List[URL]): ModelClassLoader = {
    ModelClassLoader(new URLClassLoader(urls.toArray, this.getClass.getClassLoader), urls)
  }

}