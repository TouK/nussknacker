package pl.touk.nussknacker.engine.classloader

import pl.touk.nussknacker.engine.util.StringUtils.ToUrl
import pl.touk.nussknacker.engine.util.UrlUtils.ExpandFiles

import java.nio.file.Path

object ModelClassLoaderFactory {

  private val defaultJarExtension = ".jar"

  // workingDirectoryOpt is for the purpose of easier testing. We can't easily change the working directory otherwise - see https://stackoverflow.com/a/840229
  def create(
      urls: List[String],
      workingDirectoryOpt: Option[Path],
      deploymentManagersClassLoader: DeploymentManagersClassLoader,
      jarExtension: String = defaultJarExtension
  ): ModelClassLoader = {
    val postProcessedURLs = urls.map(_.convertToURL(workingDirectoryOpt)).flatMap(_.expandFiles(jarExtension))
    new ModelClassLoader(postProcessedURLs, deploymentManagersClassLoader)
  }

}
