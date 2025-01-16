package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging

import java.io.File
import java.net.{URI, URL, URLClassLoader}
import java.nio.file.Path

case class ModelClassLoader(classLoader: ClassLoader, urls: List[URL]) {

  override def toString: String = s"ModelClassLoader(${toString(classLoader)})"

  private def toString(classLoader: ClassLoader): String = classLoader match {
    case null                => "null"
    case url: URLClassLoader => url.getURLs.mkString("URLClassLoader(List(", ", ", s"), ${toString(url.getParent)})")
    case other               => s"${other.toString}(${toString(other.getParent)})"
  }

}

object ModelClassLoader extends LazyLogging {
  // for e.g. testing in process module
  val empty: ModelClassLoader = ModelClassLoader(getClass.getClassLoader, List())

  val defaultJarExtension = ".jar"

  private def expandFiles(urls: Iterable[URL], jarExtension: String): Iterable[URL] = {
    urls.flatMap {
      case url if url.getProtocol.toLowerCase == "file" =>
        val file = new File(url.toURI)
        if (file.isDirectory) {
          val expanded =
            expandFiles(file.listFiles().filterNot(_.getName.startsWith(".")).map(_.toURI.toURL), jarExtension)
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

  private def convertToURL(urlString: String, workingDirectoryOpt: Option[Path]): URL = {
    val uri = new URI(urlString)
    if (uri.isAbsolute) {
      uri.toURL
    } else {
      val pathPart = uri.getSchemeSpecificPart
      val path = workingDirectoryOpt.map { workingDirectory =>
        workingDirectory.resolve(pathPart)
      } getOrElse {
        Path.of(pathPart)
      }
      path.toUri.toURL
    }
  }

  // workingDirectoryOpt is for the purpose of easier testing. We can't easily change the working directory otherwise - see https://stackoverflow.com/a/840229
  def apply(
      urls: List[String],
      workingDirectoryOpt: Option[Path],
      jarExtension: String = defaultJarExtension
  ): ModelClassLoader = {
    val postProcessedURLs = expandFiles(urls.map(convertToURL(_, workingDirectoryOpt)), jarExtension)
    ModelClassLoader(
      new URLClassLoader(postProcessedURLs.toArray, this.getClass.getClassLoader),
      postProcessedURLs.toList
    )
  }

}
