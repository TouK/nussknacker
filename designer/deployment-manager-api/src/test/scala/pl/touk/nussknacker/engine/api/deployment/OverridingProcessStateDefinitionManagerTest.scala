package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

class OverridingProcessStateDefinitionManagerTest extends AnyFunSuite with Matchers {

  case object DefaultState extends CustomStateStatus("DEFAULT_STATE")
  case object DefaultStateToOverride extends CustomStateStatus("OVERRIDE_THIS_STATE")

  case object CustomState extends CustomStateStatus("CUSTOM_STATE")
  case object CustomStateThatOverrides extends CustomStateStatus("OVERRIDE_THIS_STATE")

  private val icon = UnknownIcon

  private val defaultStateDefinitionManager: ProcessStateDefinitionManager = new ProcessStateDefinitionManager {
    override def stateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
      DefaultState.name -> StateDefinitionDetails("Default", icon, "", "Default description"),
      DefaultStateToOverride.name -> StateDefinitionDetails("Default to override", icon, "", "Default description to override")
    )
    override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = Nil
  }

  test("should combine delegate state definitions with custom overrides") {
    // here we use default set of states and apply custom extensions and overrides
    val manager = new OverridingProcessStateDefinitionManager(
      statusDescriptionsPF = {
        case DefaultState => "Calculated description for default, e.g. schedule date"
        case CustomState => "Calculated description for custom, e.g. schedule date"
      },
      customStateDefinitions = Map(
        CustomState.name -> StateDefinitionDetails("Custom", icon, "", "Custom description"),
        CustomStateThatOverrides.name -> StateDefinitionDetails("Custom that overrides", icon, "", "Custom description that overrides")
      ),
      delegate = defaultStateDefinitionManager
    )

    // eventually expect to have 3 states: CustomState, CustomStateToOverride and DelegateState
    val definitionsMap = manager.stateDefinitions
    definitionsMap  should have size 3
    // Raw definitions that are displayed as filter options
    definitionsMap(DefaultState.name).description shouldBe "Default description"
    definitionsMap(CustomState.name).description shouldBe "Custom description"
    definitionsMap(CustomStateThatOverrides.name).description shouldBe "Custom description that overrides"

    // Description assigned to a scenario, with custom calculations
    manager.statusDescription(DefaultState) shouldBe "Calculated description for default, e.g. schedule date"
    manager.statusDescription(CustomState) shouldBe "Calculated description for custom, e.g. schedule date"
    manager.statusDescription(CustomStateThatOverrides) shouldBe "Custom description that overrides"
  }
}
