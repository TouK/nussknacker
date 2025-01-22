package pl.touk.nussknacker.engine.util.loader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.StringUtils._
import pl.touk.nussknacker.engine.util.UrlUtils._

import java.net.URL
import java.nio.file.Path
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class ModelClassLoader private (val urls: List[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {

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
  val defaultJarExtension     = ".jar"

  // workingDirectoryOpt is for the purpose of easier testing. We can't easily change the working directory otherwise - see https://stackoverflow.com/a/840229
  def apply(
      urls: List[String],
      workingDirectoryOpt: Option[Path],
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      jarExtension: String = defaultJarExtension
  ): ModelClassLoader = {
    val postProcessedURLs = urls.map(_.convertToURL(workingDirectoryOpt)).flatMap(_.expandFiles(jarExtension))
    new ModelClassLoader(postProcessedURLs, deploymentManagersClassLoader)
  }

}
