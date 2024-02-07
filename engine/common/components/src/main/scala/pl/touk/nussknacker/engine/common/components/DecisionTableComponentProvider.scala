package pl.touk.nussknacker.engine.common.components

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.config.DocsConfig

// todo: to remove?
class DecisionTableComponentProvider extends ComponentProvider {

  override val providerName: String = "decisionTable"

  override def resolveConfigForExecution(config: Config): Config = config

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override val isAutoLoaded: Boolean = true

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig = DocsConfig(config)
    import docsConfig._

    List {
      ComponentDefinition(
        name = "decision-table",
        component = DecisionTable
      ).withRelativeDocs("BasicNodes#decisiontable")
    }
  }

}
