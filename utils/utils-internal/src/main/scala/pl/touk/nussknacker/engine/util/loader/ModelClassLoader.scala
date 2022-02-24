package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging

import java.io.File
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
  val empty: ModelClassLoader = ModelClassLoader(getClass.getClassLoader, List())

  private def expandFiles(urls: Traversable[URL]): Traversable[URL] = {
    urls.flatMap {
      case url if url.getProtocol.toLowerCase == "file" =>
        val file = new File(url.toURI)
        if (file.isDirectory) {
          expandFiles(file.listFiles().map(_.toURI.toURL))
        } else {
          List(url)
        }
      case url => List(url)
    }
  }

  def apply(urls: List[URL]): ModelClassLoader = {
    val expandedUrls = expandFiles(urls)
    ModelClassLoader(new URLClassLoader(expandedUrls.toArray, this.getClass.getClassLoader), expandedUrls.toList)
  }

}