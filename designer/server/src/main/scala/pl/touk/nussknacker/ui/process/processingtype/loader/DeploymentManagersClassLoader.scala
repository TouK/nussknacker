package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.UrlUtils.ExpandFiles

import java.net.URL
import java.nio.file.Path
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

object DeploymentManagersClassLoader extends LazyLogging {

  def create(managersDirs: List[Path]): Resource[IO, DeploymentManagersClassLoader] = {
    Resource.make(
      acquire = IO.delay {
        logger.debug(
          s"Loading deployment managers from the following locations: ${managersDirs.map(_.toString).mkString(", ")}"
        )
        new DeploymentManagersClassLoader(
          managersDirs.flatMap(_.toUri.toURL.expandFiles(".jar")),
          this.getClass.getClassLoader
        )
      }
    )(
      release = loader => IO.delay(loader.close())
    )
  }

}

class DeploymentManagersClassLoader private (urls: Seq[URL], parent: ClassLoader) extends URLClassLoader(urls, parent)
