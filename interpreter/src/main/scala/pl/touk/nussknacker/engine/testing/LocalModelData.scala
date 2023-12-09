package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentInfo, ComponentProvider}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionExtractor,
  ComponentDefinitionWithImplementation
}
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionFromConfigCreatorExtractor}
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

object LocalModelData {

  def apply(
      inputConfig: Config,
      configCreator: ProcessConfigCreator,
      components: List[ComponentDefinition],
      category: Option[String] = None,
      migrations: ProcessMigrations = ProcessMigrations.empty,
      modelConfigLoader: ModelConfigLoader = new DefaultModelConfigLoader,
      modelClassLoader: ModelClassLoader = ModelClassLoader.empty,
      objectNaming: ObjectNaming = DefaultNamespacedObjectNaming
  ): LocalModelData =
    new LocalModelData(
      InputConfigDuringExecution(inputConfig),
      modelConfigLoader,
      category,
      configCreator,
      migrations,
      modelClassLoader,
      objectNaming,
      components
    )

}

case class LocalModelData(
    inputConfigDuringExecution: InputConfigDuringExecution,
    modelConfigLoader: ModelConfigLoader,
    category: Option[String],
    configCreator: ProcessConfigCreator,
    migrations: ProcessMigrations,
    modelClassLoader: ModelClassLoader,
    objectNaming: ObjectNaming,
    components: List[ComponentDefinition]
) extends ModelData {

  override protected def extractModelDefinition: ModelDefinition[ComponentDefinitionWithImplementation] = {
    // To avoid classloading magic, for local model we load components manuall
    ModelDefinitionFromConfigCreatorExtractor
      .extractModelDefinition(
        configCreator,
        ProcessObjectDependencies(modelConfig, objectNaming),
        category
      )
      .addComponents(components.map(ComponentDefinitionExtractor.extract))
  }

}
