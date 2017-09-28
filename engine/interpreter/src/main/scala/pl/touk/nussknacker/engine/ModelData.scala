package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, ConfigCreatorTestInfoProvider, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.loader.{JarClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}

object ModelData {

  def apply(config: Config, jarPathKey: String, configKey: String) : ModelData = {
    val processConfig = config.getConfig(configKey)
    val jarClassLoader = JarClassLoader(config.getString(jarPathKey))
    ModelData(processConfig, jarClassLoader)
  }

}

//TODO: make this more of a trait, so that it's not always necessary to init full class with classloaders..
case class ModelData(processConfig: Config, jarClassLoader: JarClassLoader)
  extends ConfigCreatorSignalDispatcher with ConfigCreatorTestInfoProvider {

  //this is not lazy, to be able to detect if creator can be created...
  val configCreator : ProcessConfigCreator = ProcessConfigCreatorLoader.loadProcessConfigCreator(jarClassLoader.classLoader)

  lazy val processDefinition: ProcessDefinition[ObjectDefinition] =
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }

  lazy val validator: ProcessValidator = ProcessValidator.default(processDefinition)

  lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](jarClassLoader.classLoader)) match {
      case Empty() => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }

}
