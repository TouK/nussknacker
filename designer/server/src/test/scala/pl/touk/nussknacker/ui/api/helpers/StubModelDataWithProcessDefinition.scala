package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessConfigCreator}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class StubModelDataWithProcessDefinition(definition: ProcessDefinition[DefinitionExtractor.ObjectDefinition]) extends ModelData {

  override def migrations: ProcessMigrations = ProcessMigrations.empty

  override def configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator()

  override def modelClassLoader: ModelClassLoader = ModelClassLoader.empty

  override def modelConfigLoader: ModelConfigLoader = new DefaultModelConfigLoader

  override def objectNaming: ObjectNaming = DefaultNamespacedObjectNaming

  override def inputConfigDuringExecution: InputConfigDuringExecution = InputConfigDuringExecution(ConfigFactory.empty())

  override lazy val processWithObjectsDefinition: ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef] = ProcessDefinitionBuilder.withEmptyObjects(definition)

  override lazy val processDefinition: ProcessDefinition[DefinitionExtractor.ObjectDefinition] = definition

}
