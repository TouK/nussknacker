package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.BuiltInComponentInfo
import pl.touk.nussknacker.engine.definition.component.{
  ComponentStaticDefinition,
  CustomComponentSpecificData,
  FragmentSpecificData
}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.definition.UINodeEdges

object EdgeTypesPreparer {

  def prepareEdgeTypes(
      definitions: ModelDefinition[ComponentStaticDefinition],
  ): List[UINodeEdges] = {
    val fragmentOutputs = definitions.components.collect {
      case (
            componentInfo,
            ComponentStaticDefinition(_, _, _, _, FragmentSpecificData(outputNames))
          ) =>
        // TODO: enable choice of output type
        UINodeEdges(
          componentInfo,
          outputNames.map(EdgeType.FragmentOutput),
          canChooseNodes = false,
          isForInputDefinition = false
        )
    }

    val joinInputs = definitions.components.collect {
      case (info, ComponentStaticDefinition(_, _, _, _, CustomComponentSpecificData(true, _))) =>
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
    ) ++ fragmentOutputs ++ joinInputs
  }

}
