package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, ProcessActionType}

class OverridingProcessStateDefinitionManagerTest extends AnyFunSuite with Matchers {

  case object DelegateState extends CustomStateStatus("DELEGATE_STATE")
  case object DelegateStateToOverride extends CustomStateStatus("OVERRIDE_THIS_STATE")

  case object CustomState extends CustomStateStatus("CUSTOM_STATE")
  case object CustomStateToOverride extends CustomStateStatus("OVERRIDE_THIS_STATE")

  private val delegateStateDefinitionManager: ProcessStateDefinitionManager = new ProcessStateDefinitionManager {
    override def stateDefinitions(): Set[StateDefinition] = Set(
      StateDefinition(DelegateState.name, "Dummy 1", None, None, Some("Description 1")),
      StateDefinition(DelegateStateToOverride.name, "Dummy 2", None, None, Some("Description 2"))
    )
    override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = stateStatus match {
      case DelegateState => List(Deploy)
      case DelegateStateToOverride => List(Deploy, Cancel)
      case _ => Nil
    }
    override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus = stateAction match {
      case Deploy => DelegateState
      case Cancel => DelegateStateToOverride
      case _ => FailedStateStatus("UNKNOWN_ACTION")
    }
  }

  test("should combine delegate state definitions with overrides") {
    // here we expect to have 3 states: CustomState, CustomStateToOverride and DelegateState
    val manager = new OverridingProcessStateDefinitionManager(
      statusActionsPF = {
        case CustomStateToOverride => List(Cancel)
      },
      statusDescriptionsPF = {
        case DelegateState => Some("Calculated description for delegate, e.g. schedule date")
        case CustomState => Some("Calculated description for custom, e.g. schedule date")
      },
      stateDefinitions = Set(
        StateDefinition(CustomState.name, "Custom 1", None, None, Some("Default description for custom")),
        StateDefinition(CustomStateToOverride.name, "Custom 2", None, None, Some("Default description for custom 2"))
      ),
      delegate = delegateStateDefinitionManager
    )

    //FIXME: manager.stateDefinitions() should have size 3
    val definitionsMap = manager.stateDefinitions().toMapByName
    definitionsMap  should have size 3
    // Raw definitions that are displayed as filter options
    definitionsMap(DelegateState.name).description shouldBe Some("Description 1")
    definitionsMap(CustomState.name).description shouldBe Some("Default description for custom")
    definitionsMap(CustomStateToOverride.name).description shouldBe Some("Default description for custom 2")

    // Description assigned to a scenario, with custom calculations
    manager.statusDescription(DelegateState) shouldBe Some("Calculated description for delegate, e.g. schedule date")
    manager.statusDescription(CustomState) shouldBe Some("Calculated description for custom, e.g. schedule date")
    manager.statusDescription(CustomStateToOverride) shouldBe Some("Default description for custom 2")

  }
}
