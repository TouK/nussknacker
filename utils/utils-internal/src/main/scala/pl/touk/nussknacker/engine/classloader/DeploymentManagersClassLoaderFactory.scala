package pl.touk.nussknacker.engine.classloader

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.UrlUtils.ExpandFiles

import java.nio.file.{Files, Path}

object DeploymentManagersClassLoaderFactory extends LazyLogging {

  def create(managersDirs: List[Path]): Resource[IO, DeploymentManagersClassLoader] = {
    val invalidPaths = managersDirs
      .map(p => (p, !Files.isDirectory(p)))
      .collect { case (p, true) => p }

    if (invalidPaths.nonEmpty)
      throw new IllegalArgumentException(
        s"Cannot find the following directories: ${invalidPaths.mkString(", ")}"
      )

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
