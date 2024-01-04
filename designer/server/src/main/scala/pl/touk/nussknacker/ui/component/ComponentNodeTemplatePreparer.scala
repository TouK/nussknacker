package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentGroupName, ComponentInfo}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component._
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.definition.ComponentNodeTemplate
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser

private[component] object ComponentNodeTemplatePreparer {

  def componentNodeTemplatesWithGroupNames(
      user: LoggedUser,
      definitions: ModelDefinition[ComponentStaticDefinition],
      processCategoryService: ProcessCategoryService,
      processingType: ProcessingType
  ): List[(ComponentGroupName, ComponentNodeTemplate)] = {
    val userCategoryService          = new UserCategoryService(processCategoryService)
    val userCategories               = userCategoryService.getUserCategories(user)
    val processingTypeCategories     = List(processCategoryService.getProcessingTypeCategoryUnsafe(processingType))
    val userProcessingTypeCategories = userCategories.intersect(processingTypeCategories)

    def filterCategories(componentDefinition: ComponentStaticDefinition): List[String] =
      userProcessingTypeCategories.intersect(
        componentDefinition.categories.getOrElse(processCategoryService.getAllCategories)
      )

    def parameterTemplates(componentDefinition: ComponentStaticDefinition): List[NodeParameter] =
      NodeTemplateParameterPreparer.prepareNodeParameterTemplates(componentDefinition.parameters)

    def serviceRef(info: ComponentInfo, componentDefinition: ComponentStaticDefinition) =
      ServiceRef(info.name, parameterTemplates(componentDefinition))

    def prepareComponentNodeTemplateWithGroup(info: ComponentInfo, componentDefinition: ComponentStaticDefinition) = {
      val nodeTemplate = (info, componentDefinition.componentTypeSpecificData) match {
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
        case (info, ServiceSpecificData) if componentDefinition.hasReturn =>
          Enricher("", serviceRef(info, componentDefinition), "output")
        case (info, ServiceSpecificData) =>
          Processor("", serviceRef(info, componentDefinition))
        case (info, CustomComponentSpecificData(true, _)) =>
          Join(
            "",
            if (componentDefinition.hasReturn) Some("outputVar") else None,
            info.name,
            parameterTemplates(componentDefinition),
            List.empty
          )
        case (info, CustomComponentSpecificData(false, _)) =>
          CustomNode(
            "",
            if (componentDefinition.hasReturn) Some("outputVar") else None,
            info.name,
            parameterTemplates(componentDefinition)
          )
        case (info, SinkSpecificData) =>
          Sink("", SinkRef(info.name, parameterTemplates(componentDefinition)))
        case (info, SourceSpecificData) =>
          Source("", SourceRef(info.name, parameterTemplates(componentDefinition)))
        case (info, FragmentSpecificData(outputNames)) =>
          val outputs = outputNames.map(name => (name, name)).toMap
          FragmentInput("", FragmentRef(info.name, parameterTemplates(componentDefinition), outputs))
        case (_, BuiltInComponentSpecificData | GlobalVariablesSpecificData) =>
          throw new IllegalStateException(s"Not expected component: $info with definition: $componentDefinition")
      }
      val branchParametersTemplate =
        NodeTemplateParameterPreparer.prepareNodeBranchParameterTemplates(componentDefinition.parameters)
      val componentNodeTemplate = ComponentNodeTemplate.create(
        info,
        nodeTemplate,
        filterCategories(componentDefinition),
        branchParametersTemplate
      )
      val componentGroup = componentDefinition.componentGroupUnsafe
      (componentGroup, componentNodeTemplate)
    }

    definitions.components.toList.map(prepareComponentNodeTemplateWithGroup _ tupled)
  }

}
