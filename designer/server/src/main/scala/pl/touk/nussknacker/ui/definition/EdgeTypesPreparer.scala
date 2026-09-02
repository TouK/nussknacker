package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.BuiltInComponentId
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  CustomComponentSpecificData,
  FragmentSpecificData
}
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.definition.UINodeEdges

object EdgeTypesPreparer {

  def prepareEdgeTypes(components: List[ComponentDefinitionWithImplementation]): List[UINodeEdges] = {
    val fromComponents = components
      .map { component =>
        (component.id, component.componentTypeSpecificData)
      }
      .collect {
        case (
              componentId,
              FragmentSpecificData(outputNames)
            ) =>
          // TODO: enable choice of output type
          UINodeEdges(
            componentId,
            outputNames.map(EdgeType.FragmentOutput),
            canChooseNodes = false,
            isForInputDefinition = false
          )
        case (id, CustomComponentSpecificData(true, _, _)) =>
          UINodeEdges(id, List.empty, canChooseNodes = true, isForInputDefinition = true)
        // Single-output components deliberately get no entry: without one the frontend keeps drawing and persisting
        // the released unnamed main edge, so their scenarios stay untouched.
        // TODO: unify single-output components with this model - emit the entries (and so persist the named main
        // edge) for every custom component. Requires relaxing the named-edge validation for single-output components
        // and deciding migration vs permanent tolerance for the unnamed main edges of released scenarios.
        case (id, CustomComponentSpecificData(false, _, outputs)) if outputs.tail.nonEmpty =>
          UINodeEdges(
            id,
            outputs.toList.map(o => EdgeType.CustomNodeOutput(o.name)),
            canChooseNodes = false,
            isForInputDefinition = false
          )
      }

    List(
      UINodeEdges(
        BuiltInComponentId.Split,
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentId.Choice,
        List(EdgeType.NextSwitch(Expression.spel("true")), EdgeType.SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentId.Filter,
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      )
    ) ::: fromComponents
  }

}
