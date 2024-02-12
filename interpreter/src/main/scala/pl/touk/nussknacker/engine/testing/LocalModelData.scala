package pl.touk.nussknacker.engine.testing

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentDefinition,
  ComponentId,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.namespaces.{NamingStrategy, NamingStrategyProvider}
import pl.touk.nussknacker.engine.api.process.{
  EmptyProcessConfigCreator,
  ProcessConfigCreator,
  ProcessObjectDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionFromConfigCreatorExtractor}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{
  ComponentsUiConfigParser,
  DefaultModelConfigLoader,
  InputConfigDuringExecution,
  ModelConfigLoader
}
import pl.touk.nussknacker.engine.testing.LocalModelData.ExtractDefinitionFunImpl
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

object LocalModelData {

  def apply(
      inputConfig: Config,
      components: List[ComponentDefinition],
      // Warning, ProcessConfigCreator will be faded out soon. Please try to use components list instead of it.
      // For Flink, in some cases it may require to make Component Serializable
      configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator,
      category: Option[String] = None,
      migrations: ProcessMigrations = ProcessMigrations.empty,
      modelConfigLoader: ModelConfigLoader = new DefaultModelConfigLoader,
      modelClassLoader: ModelClassLoader = ModelClassLoader.empty,
      determineDesignerWideId: ComponentId => DesignerWideComponentId = DesignerWideComponentId.default("streaming", _),
      additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig] = Map.empty,
      namingStrategy: Option[NamingStrategy] = None
  ): LocalModelData =
    new LocalModelData(
      InputConfigDuringExecution(inputConfig),
      modelConfigLoader,
      category,
      configCreator,
      migrations,
      modelClassLoader,
      namingStrategy = namingStrategy.getOrElse(NamingStrategyProvider(inputConfig)),
      components,
      determineDesignerWideId,
      additionalConfigsFromProvider
    )

  class ExtractDefinitionFunImpl(
      configCreator: ProcessConfigCreator,
      category: Option[String],
      components: List[ComponentDefinition]
  ) extends ExtractDefinitionFun
      with Serializable {

    override def apply(
        classLoader: ClassLoader,
        modelDependencies: ProcessObjectDependencies,
        determineDesignerWideId: ComponentId => DesignerWideComponentId,
        additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
    ): ModelDefinition = {
      val componentsUiConfig = ComponentsUiConfigParser.parse(modelDependencies.config)
      val componentsDefWithImpl = ComponentDefinitionWithImplementation.forList(
        components,
        componentsUiConfig,
        determineDesignerWideId,
        additionalConfigsFromProvider
      )
      // To avoid classloading magic, for local model we load components manually and skip ComponentProvider's loading
      ModelDefinitionFromConfigCreatorExtractor
        .extractModelDefinition(
          configCreator,
          category,
          modelDependencies,
          componentsUiConfig,
          determineDesignerWideId,
          additionalConfigsFromProvider
        )
        .withComponents(componentsDefWithImpl)
    }

  }

}

case class LocalModelData(
    inputConfigDuringExecution: InputConfigDuringExecution,
    modelConfigLoader: ModelConfigLoader,
    category: Option[String],
    configCreator: ProcessConfigCreator,
    migrations: ProcessMigrations,
    modelClassLoader: ModelClassLoader,
    namingStrategy: NamingStrategy,
    components: List[ComponentDefinition],
    determineDesignerWideId: ComponentId => DesignerWideComponentId,
    additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
) extends ModelData {

  override val extractModelDefinitionFun = new ExtractDefinitionFunImpl(configCreator, category, components)

}
