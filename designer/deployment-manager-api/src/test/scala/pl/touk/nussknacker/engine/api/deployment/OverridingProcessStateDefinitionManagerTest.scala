package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName

class OverridingProcessStateDefinitionManagerTest extends AnyFunSuite with Matchers {

  case object DefaultState extends CustomStateStatus("DEFAULT_STATE")
  case object DefaultStateToOverride extends CustomStateStatus("OVERRIDE_THIS_STATE")

  case object CustomState extends CustomStateStatus("CUSTOM_STATE")
  case object CustomStateThatOverrides extends CustomStateStatus("OVERRIDE_THIS_STATE")

  private val defaultStateDefinitionManager: ProcessStateDefinitionManager = new ProcessStateDefinitionManager {
    override def stateDefinitions(): Map[StatusName, StateDefinition] = Map(
      DefaultState.name -> StateDefinition("Default", None, None, Some("Default description")),
      DefaultStateToOverride.name -> StateDefinition("Default to override", None, None, Some("Default description to override"))
    )
    override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = Nil
    override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus = FailedStateStatus("UNKNOWN_ACTION")
  }

  test("should combine delegate state definitions with overrides") {
    // here we use default set of states and apply extensions and overrides
    val manager = new OverridingProcessStateDefinitionManager(
      statusDescriptionsPF = {
        case DefaultState => Some("Calculated description for default, e.g. schedule date")
        case CustomState => Some("Calculated description for custom, e.g. schedule date")
      },
      stateDefinitions = Map(
        CustomState.name -> StateDefinition("Custom", None, None, Some("Custom description")),
        CustomStateThatOverrides.name -> StateDefinition("Custom that overrides", None, None, Some("Custom description that overrides"))
      ),
      delegate = defaultStateDefinitionManager
    )

    // eventually expect to have 3 states: CustomState, CustomStateToOverride and DelegateState
    val definitionsMap = manager.stateDefinitions()
    definitionsMap  should have size 3
    // Raw definitions that are displayed as filter options
    definitionsMap(DefaultState.name).description shouldBe Some("Default description")
    definitionsMap(CustomState.name).description shouldBe Some("Custom description")
    definitionsMap(CustomStateThatOverrides.name).description shouldBe Some("Custom description that overrides")

    // Description assigned to a scenario, with custom calculations
    manager.statusDescription(DefaultState) shouldBe Some("Calculated description for default, e.g. schedule date")
    manager.statusDescription(CustomState) shouldBe Some("Calculated description for custom, e.g. schedule date")
    manager.statusDescription(CustomStateThatOverrides) shouldBe Some("Custom description that overrides")
  }
}
