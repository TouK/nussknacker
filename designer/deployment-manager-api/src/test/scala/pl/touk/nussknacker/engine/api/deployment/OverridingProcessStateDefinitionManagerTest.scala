package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessStateDefinitionManager.ScenarioStatusWithScenarioContext
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.process.VersionId

class OverridingProcessStateDefinitionManagerTest extends AnyFunSuite with Matchers {

  val DefaultState: StateStatus           = StateStatus("DEFAULT_STATE")
  val DefaultStateToOverride: StateStatus = StateStatus("OVERRIDE_THIS_STATE")

  val CustomState: StateStatus              = StateStatus("CUSTOM_STATE")
  val CustomStateThatOverrides: StateStatus = StateStatus("OVERRIDE_THIS_STATE")

  private val icon = UnknownIcon

  private val defaultStateDefinitionManager: ProcessStateDefinitionManager = new ProcessStateDefinitionManager {

    override def stateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
      DefaultState.name -> StateDefinitionDetails("Default", icon, "dummy", "Default description"),
      DefaultStateToOverride.name -> StateDefinitionDetails(
        "Default to override",
        icon,
        "dummy",
        "Default description to override"
      )
    )

    override def statusActions(input: ScenarioStatusWithScenarioContext): Set[ScenarioActionName] = Set.empty
  }

  test("should combine delegate state definitions with custom overrides") {
    // here we use default set of states and apply custom extensions and overrides
    val manager = new OverridingProcessStateDefinitionManager(
      statusDescriptionsPF = {
        case DefaultState => "Calculated description for default, e.g. schedule date"
        case CustomState  => "Calculated description for custom, e.g. schedule date"
      },
      customStateDefinitions = Map(
        CustomState.name -> StateDefinitionDetails("Custom", icon, "dummy", "Custom description"),
        CustomStateThatOverrides.name -> StateDefinitionDetails(
          "Custom that overrides",
          icon,
          "dummy",
          "Custom description that overrides"
        )
      ),
      delegate = defaultStateDefinitionManager
    )

    // eventually expect to have 3 states: CustomState, CustomStateToOverride and DelegateState
    val definitionsMap = manager.stateDefinitions
    definitionsMap should have size 3
    // Raw definitions that are displayed as filter options
    definitionsMap(DefaultState.name).description shouldBe "Default description"
    definitionsMap(CustomState.name).description shouldBe "Custom description"
    definitionsMap(CustomStateThatOverrides.name).description shouldBe "Custom description that overrides"

    def toInput(status: StateStatus) =
      ScenarioStatusWithScenarioContext(status, VersionId(1), None, None)

    // Description assigned to a scenario, with custom calculations
    manager.statusDescription(toInput(DefaultState)) shouldBe "Calculated description for default, e.g. schedule date"
    manager.statusDescription(toInput(CustomState)) shouldBe "Calculated description for custom, e.g. schedule date"
    manager.statusDescription(toInput(CustomStateThatOverrides)) shouldBe "Custom description that overrides"
  }

}
