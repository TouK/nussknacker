package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentGroupName, ComponentId}
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
      components: List[ComponentWithStaticDefinition]
  ): List[ComponentNodeTemplateWithGroupNames] = {
    def parameterTemplates(staticDefinition: ComponentStaticDefinition): List[NodeParameter] =
      NodeParameterTemplatesPreparer.prepareNodeParameterTemplates(staticDefinition.parameters)

    def serviceRef(id: ComponentId, staticDefinition: ComponentStaticDefinition) =
      ServiceRef(id.name, parameterTemplates(staticDefinition))

    def prepareComponentNodeTemplateWithGroup(
        component: ComponentDefinitionWithImplementation,
        staticDefinition: ComponentStaticDefinition
    ) = {
      val nodeTemplate = (component.id, component.componentTypeSpecificData) match {
        case (BuiltInComponentId.Filter, _) =>
          Filter("", Expression.spel("true"))
        case (BuiltInComponentId.Split, _) =>
          Split("")
        case (BuiltInComponentId.Choice, _) =>
          Switch("")
        case (BuiltInComponentId.Variable, _) =>
          Variable("", "varName", Expression.spel("'value'"))
        case (BuiltInComponentId.RecordVariable, _) =>
          VariableBuilder("", "varName", List(Field("fieldName", Expression.spel("'value'"))))
        case (BuiltInComponentId.FragmentInputDefinition, _) =>
          FragmentInputDefinition("", List.empty)
        case (BuiltInComponentId.FragmentOutputDefinition, _) =>
          FragmentOutputDefinition("", "output", List.empty)
        case (id, ServiceSpecificData) if staticDefinition.hasReturn =>
          Enricher("", serviceRef(id, staticDefinition), "output")
        case (id, ServiceSpecificData) =>
          Processor("", serviceRef(id, staticDefinition))
        case (id, CustomComponentSpecificData(true, _)) =>
          Join(
            "",
            if (staticDefinition.hasReturn) Some("outputVar") else None,
            id.name,
            parameterTemplates(staticDefinition),
            List.empty
          )
        case (id, CustomComponentSpecificData(false, _)) =>
          CustomNode(
            "",
            if (staticDefinition.hasReturn) Some("outputVar") else None,
            id.name,
            parameterTemplates(staticDefinition)
          )
        case (id, SinkSpecificData) =>
          Sink("", SinkRef(id.name, parameterTemplates(staticDefinition)))
        case (id, SourceSpecificData) =>
          Source("", SourceRef(id.name, parameterTemplates(staticDefinition)))
        case (id, FragmentSpecificData(outputNames)) =>
          val outputs = outputNames.map(name => (name, name)).toMap
          FragmentInput("", FragmentRef(id.name, parameterTemplates(staticDefinition), outputs))
        case (_, BuiltInComponentSpecificData) =>
          throw new IllegalStateException(s"Not expected component: $component")
      }
      val branchParametersTemplate =
        NodeParameterTemplatesPreparer.prepareNodeBranchParameterTemplates(staticDefinition.parameters)
      val componentNodeTemplate = UIComponentNodeTemplate.create(
        component.id,
        nodeTemplate,
        branchParametersTemplate,
        component.label.getOrElse(component.name)
      )
      ComponentNodeTemplateWithGroupNames(
        componentNodeTemplate,
        component.originalGroupName,
        component.componentGroup
      )
    }

    components.map { withStatic =>
      prepareComponentNodeTemplateWithGroup(withStatic.component, withStatic.staticDefinition)
    }
  }

}

private[component] case class ComponentNodeTemplateWithGroupNames(
    nodeTemplate: UIComponentNodeTemplate,
    originalGroupName: ComponentGroupName,
    mappedGroupName: ComponentGroupName
)
