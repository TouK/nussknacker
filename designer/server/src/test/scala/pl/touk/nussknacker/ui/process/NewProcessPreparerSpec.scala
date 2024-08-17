package pl.touk.nussknacker.ui.process

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.SingleScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer

class NewProcessPreparerSpec extends AnyFlatSpec with Matchers {

  it should "create new empty process" in {
    val preparer = new NewProcessPreparer(
      ProcessTestData.streamingTypeSpecificInitialData,
      Map.empty,
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify)
    )

    val emptyProcess = preparer.prepareEmptyProcess(ProcessName("processId1"), isFragment = false)

    emptyProcess.name shouldBe ProcessName("processId1")
    emptyProcess.nodes shouldBe List.empty
  }

  it should "override scenario properties initial values with values from provider" in {
    val preparer = new NewProcessPreparer(
      ProcessTestData.streamingTypeSpecificInitialData,
      Map(
        TestAdditionalUIConfigProvider.scenarioPropertyName -> SingleScenarioPropertyConfig.empty.copy(
          defaultValue = Some("defaultToBeOverridden")
        )
      ),
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify)
    )

    val emptyProcess = preparer.prepareEmptyProcess(ProcessName("processId1"), isFragment = false)

    emptyProcess.metaData.additionalFields.properties should contain(
      TestAdditionalUIConfigProvider.scenarioPropertyName -> "defaultValueOverride"
    )
  }

}
