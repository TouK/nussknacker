package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.util.UrlUtils._

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.Path

case class ModelClassLoader private (classLoader: ClassLoader, urls: List[URL]) {

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
  val defaultJarExtension     = ".jar"

  // workingDirectoryOpt is for the purpose of easier testing. We can't easily change the working directory otherwise - see https://stackoverflow.com/a/840229
  def apply(
      urls: List[String],
      workingDirectoryOpt: Option[Path],
      jarExtension: String = defaultJarExtension
  ): ModelClassLoader = {
    val postProcessedURLs = validateExistence(
      urls.map(_.convertToURL(workingDirectoryOpt)).flatMap(_.expandFiles(jarExtension))
    )
    ModelClassLoader(
      new URLClassLoader(postProcessedURLs.toArray, this.getClass.getClassLoader),
      postProcessedURLs.toList
    )
  }

  private def validateExistence(urls: Iterable[URL]): Iterable[URL] = {
    urls.filterNot(url => doesExist(url)).toList match {
      case Nil => urls
      case notExisted =>
        throw new IllegalArgumentException(s"The following URLs don't exist: [${notExisted.mkString(",")}]")
    }
  }

  private def doesExist(url: URL): Boolean = {
    url.getProtocol match {
      case "file" =>
        val file = new File(url.toURI)
        file.exists() && file.isFile
      case _ =>
        false
    }
  }

}
