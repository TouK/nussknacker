package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, ConfigCreatorTestInfoProvider, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.JarClassLoader

object ModelData {

  def apply(config: Config, jarPathKey: String, configKey: String) : ModelData = {
    val processConfig = config.getConfig(configKey)
    val jarClassLoader = JarClassLoader(config.getString(jarPathKey))
    ModelData(processConfig, jarClassLoader)
  }

}

case class ModelData(processConfig: Config, jarClassLoader: JarClassLoader)
  extends ConfigCreatorSignalDispatcher with ConfigCreatorTestInfoProvider {

  //this is not lazy, to be able to detect if creator can be created...
  val configCreator : ProcessConfigCreator = jarClassLoader.createProcessConfigCreator

  lazy val processDefinition : ProcessDefinition[ObjectDefinition] = ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
    ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
  }

  lazy val validator : ProcessValidator = ProcessValidator.default(processDefinition)

  lazy val migrations : Option[ProcessMigrations] = {
    jarClassLoader.loadServices[ProcessMigrations] match {
      case Nil => None
      case migrationsDef :: Nil => Some(migrationsDef)
      case moreThanOne => throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }
  
}
