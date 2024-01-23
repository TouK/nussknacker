package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentGroupName, ComponentInfo}
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.definition.UIComponentNodeTemplate

private[component] object ComponentNodeTemplatePreparer {

  def componentNodeTemplatesWithGroupNames(
      components: Map[ComponentInfo, ComponentWithStaticDefinition]
  ): List[ComponentNodeTemplateWithGroupNames] = {
    def parameterTemplates(staticDefinition: ComponentStaticDefinition): List[NodeParameter] =
      NodeParameterTemplatesPreparer.prepareNodeParameterTemplates(staticDefinition.parameters)

    def serviceRef(info: ComponentInfo, staticDefinition: ComponentStaticDefinition) =
      ServiceRef(info.name, parameterTemplates(staticDefinition))

    def prepareComponentNodeTemplateWithGroup(
        info: ComponentInfo,
        component: ComponentDefinitionWithImplementation,
        staticDefinition: ComponentStaticDefinition
    ) = {
      val nodeTemplate = (info, component.componentTypeSpecificData) match {
        case (BuiltInComponentInfo.Filter, _) =>
          Filter("", Expression.spel("true"))
        case (BuiltInComponentInfo.Split, _) =>
          Split("")
        case (BuiltInComponentInfo.Choice, _) =>
          Switch("")
        case (BuiltInComponentInfo.Variable, _) =>
          Variable("", "varName", Expression.spel("'value'"))
        case (BuiltInComponentInfo.RecordVariable, _) =>
          VariableBuilder("", "varName", List(Field("fieldName", Expression.spel("'value'"))))
        case (BuiltInComponentInfo.FragmentInputDefinition, _) =>
          FragmentInputDefinition("", List.empty)
        case (BuiltInComponentInfo.FragmentOutputDefinition, _) =>
          FragmentOutputDefinition("", "output", List.empty)
        case (info, ServiceSpecificData) if staticDefinition.hasReturn =>
          Enricher("", serviceRef(info, staticDefinition), "output")
        case (info, ServiceSpecificData) =>
          Processor("", serviceRef(info, staticDefinition))
        case (info, CustomComponentSpecificData(true, _)) =>
          Join(
            "",
            if (staticDefinition.hasReturn) Some("outputVar") else None,
            info.name,
            parameterTemplates(staticDefinition),
            List.empty
          )
        case (info, CustomComponentSpecificData(false, _)) =>
          CustomNode(
            "",
            if (staticDefinition.hasReturn) Some("outputVar") else None,
            info.name,
            parameterTemplates(staticDefinition)
          )
        case (info, SinkSpecificData) =>
          Sink("", SinkRef(info.name, parameterTemplates(staticDefinition)))
        case (info, SourceSpecificData) =>
          Source("", SourceRef(info.name, parameterTemplates(staticDefinition)))
        case (info, FragmentSpecificData(outputNames)) =>
          val outputs = outputNames.map(name => (name, name)).toMap
          FragmentInput("", FragmentRef(info.name, parameterTemplates(staticDefinition), outputs))
        case (_, BuiltInComponentSpecificData) =>
          throw new IllegalStateException(s"Not expected component: $info with definition: $component")
      }
      val branchParametersTemplate =
        NodeParameterTemplatesPreparer.prepareNodeBranchParameterTemplates(staticDefinition.parameters)
      val componentNodeTemplate = UIComponentNodeTemplate.create(
        info,
        nodeTemplate,
        branchParametersTemplate
      )
      ComponentNodeTemplateWithGroupNames(
        componentNodeTemplate,
        component.originalGroupName,
        component.componentGroup
      )
    }

    components.toList.map { case (info, ComponentWithStaticDefinition(component, staticDefinition)) =>
      prepareComponentNodeTemplateWithGroup(info, component, staticDefinition)
    }
  }

}

private[component] case class ComponentNodeTemplateWithGroupNames(
    nodeTemplate: UIComponentNodeTemplate,
    originalGroupName: ComponentGroupName,
    mappedGroupName: ComponentGroupName
)
