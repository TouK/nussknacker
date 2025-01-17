package pl.touk.nussknacker.test.mock

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, ComponentId, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessConfigCreator}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming

class StubModelDataWithModelDefinition(
    definition: ModelDefinition,
    configDuringExecution: Config = ConfigFactory.empty()
) extends ModelData {

  override def migrations: ProcessMigrations = ProcessMigrations.empty

  override def configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator

  override def modelClassLoader: ModelClassLoader = ModelClassLoader.empty

  override def modelConfigLoader: ModelConfigLoader =
    new DefaultModelConfigLoader(_ => true)

  override def namingStrategy: NamingStrategy = NamingStrategy.Disabled

  override def inputConfigDuringExecution: InputConfigDuringExecution = InputConfigDuringExecution(
    configDuringExecution
  )

  override def category: Option[String] = None

  override def extractModelDefinitionFun: ExtractDefinitionFun = (_, _, _, _) => definition

  override def determineDesignerWideId: ComponentId => DesignerWideComponentId =
    DesignerWideComponentId.default(Streaming.stringify, _)

  override def additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig] =
    TestAdditionalUIConfigProvider.componentAdditionalConfigMap
}
