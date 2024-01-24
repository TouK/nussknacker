package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo}
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

  def prepareEdgeTypes(components: Map[ComponentInfo, ComponentDefinitionWithImplementation]): List[UINodeEdges] = {
    val fromComponents = components.toList
      .map { case (info, component) =>
        (info, component.componentTypeSpecificData)
      }
      .collect {
        case (
              componentInfo,
              FragmentSpecificData(outputNames)
            ) =>
          // TODO: enable choice of output type
          UINodeEdges(
            componentInfo,
            outputNames.map(EdgeType.FragmentOutput),
            canChooseNodes = false,
            isForInputDefinition = false
          )
        case (info, CustomComponentSpecificData(true, _)) =>
          UINodeEdges(info, List.empty, canChooseNodes = true, isForInputDefinition = true)
      }

    List(
      UINodeEdges(
        BuiltInComponentInfo.Split,
        List.empty,
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentInfo.Choice,
        List(EdgeType.NextSwitch(Expression.spel("true")), EdgeType.SwitchDefault),
        canChooseNodes = true,
        isForInputDefinition = false
      ),
      UINodeEdges(
        BuiltInComponentInfo.Filter,
        List(FilterTrue, FilterFalse),
        canChooseNodes = false,
        isForInputDefinition = false
      )
    ) ::: fromComponents
  }

}
