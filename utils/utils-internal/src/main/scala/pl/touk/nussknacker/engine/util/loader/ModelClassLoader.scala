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

  val defaultJarExtension = ".jar"

  private def expandFiles(urls: Iterable[URL], jarExtension: String): Iterable[URL] = {
    urls.flatMap {
      case url if url.getProtocol.toLowerCase == "file" =>
        val file = new File(url.toURI)
        if (file.isDirectory) {
          val expanded = expandFiles(file.listFiles().filterNot(_.getName.startsWith(".")).map(_.toURI.toURL), jarExtension)
          if (expanded.isEmpty) {
            List.empty
          } else if (expanded.exists(_.getFile.endsWith(jarExtension))) { // not expand if nested jars not exists
            expanded
          } else {
            List(url)
          }
        } else {
          List(url)
        }
      case url => List(url)
    }
  }

  def apply(urls: List[URL], jarExtension: String = defaultJarExtension): ModelClassLoader = {
    val expandedUrls = expandFiles(urls, jarExtension)
    ModelClassLoader(new URLClassLoader(expandedUrls.toArray, this.getClass.getClassLoader), expandedUrls.toList)
  }

}