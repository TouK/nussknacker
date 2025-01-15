package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.nio.file.Path

final case class ModelClassLoaderDependencies(classpath: List[String], workingDirectoryOpt: Option[Path]) {

  def show(): String = {
    val workingDirectoryReadable = workingDirectoryOpt match {
      case Some(value) => value.toString
      case None        => "None (default)"
    }
    s"classpath: ${classpath.mkString(", ")}, workingDirectoryOpt: $workingDirectoryReadable"
  }

}

class ModelClassLoaderProvider private (
    processingTypeClassLoaders: Map[String, (ModelClassLoader, ModelClassLoaderDependencies)]
) {

  def forProcessingTypeUnsafe(processingTypeName: String): ModelClassLoader = {
    processingTypeClassLoaders
      .getOrElse(
        processingTypeName,
        throw new IllegalArgumentException(
          s"Unknown ProcessingType: $processingTypeName, known ProcessingTypes are: ${processingTypeName.mkString(", ")}"
        )
      )
      ._1
  }

  def validateReloadConsistency(
      dependenciesFromReload: Map[String, ModelClassLoaderDependencies]
  ): Unit = {
    if (dependenciesFromReload.keySet != processingTypeClassLoaders.keySet) {
      throw new IllegalStateException(
        s"""Processing types cannot be added, removed, or renamed during processing type reload.
           |Reloaded processing types: [${dependenciesFromReload.keySet.toList.sorted.mkString(", ")}]
           |Current processing types: [${processingTypeClassLoaders.keySet.toList.sorted.mkString(", ")}]
           |If you need to modify this, please restart the application with desired config.""".stripMargin
      )
    }
    dependenciesFromReload.foreach { case (processingType, reloadedConfig) =>
      val currentConfig = processingTypeClassLoaders.mapValues(_._2)(processingType)
      if (reloadedConfig != currentConfig) {
        throw new IllegalStateException(
          s"Error during processing types reload. Model ClassLoader dependencies such as classpath cannot be modified during reload. " +
            s"For processing type [$processingType], reloaded ClassLoader dependencies: [${reloadedConfig.show()}] " +
            s"do not match current dependencies: [${currentConfig.show()}]"
        )
      }
    }
  }

}

object ModelClassLoaderProvider {

  def apply(processingTypeConfig: Map[String, ModelClassLoaderDependencies]): ModelClassLoaderProvider = {
    val processingTypesClassloaders = processingTypeConfig.map { case (name, deps) =>
      name -> (ModelClassLoader(deps.classpath, deps.workingDirectoryOpt) -> deps)
    }
    new ModelClassLoaderProvider(processingTypesClassloaders)
  }

}
