package pl.touk.nussknacker.engine.definition.component

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentId, ParameterConfig}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DynamicComponent,
  JoinDynamicComponent,
  SingleInputDynamicComponent,
  WithStaticParameters
}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.compile.nodecompilation.DynamicNodeValidator
import pl.touk.nussknacker.engine.definition.component.DynamicComponentStaticDefinitionDeterminer.staticReturnType
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.parameter.StandardParameterEnrichment

// This class purpose is to provide initial set of parameters that will be presented after first usage of a component.
// It is necessary to provide them, because:
// - We want to avoid flickering of parameters after first entering into the node
// - Sometimes user want to just use the component without filling parameters with own data - in this case we want to make sure
//   that parameters will be available in the scenario, even with a default values
class DynamicComponentStaticDefinitionDeterminer(
    nodeValidator: DynamicNodeValidator,
    createMetaData: ProcessName => MetaData
) extends LazyLogging {

  private def determineStaticDefinition(
      dynamic: DynamicComponentDefinitionWithImplementation
  ): ComponentStaticDefinition = {
    val parameters = determineInitialParameters(dynamic.component, dynamic.parametersConfig)
    val parametersWithoutEnrichments =
      determineInitialParameters(dynamic.component, dynamic.parametersWithoutEnrichmentsConfig)
    ComponentStaticDefinition(
      parameters = parameters,
      returnType = staticReturnType(dynamic.component),
      parametersWithoutEnrichments = parametersWithoutEnrichments
    )
  }

  private def determineInitialParameters(
      component: DynamicComponent[_],
      parametersConfig: Map[ParameterName, ParameterConfig]
  ): List[Parameter] = {
    def inferParameters(transformer: DynamicComponent[_])(inputContext: transformer.InputContext) = {
      // TODO: We could determine initial parameters when component is firstly used in scenario instead of during loading model data
      //       Thanks to that, instead of passing fake nodeId/metaData and empty additionalFields, we could pass the real once
      val scenarioName              = ProcessName("fakeScenarioName")
      val metaData                  = createMetaData(scenarioName)
      implicit val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = scenarioName))
      implicit val nodeId: NodeId   = NodeId("fakeNodeId")
      nodeValidator
        .validateNode(
          transformer,
          Nil,
          Nil,
          if (component.nodeDependencies.contains(OutputVariableNameDependency)) Some("fakeOutputVariable")
          else None,
          parametersConfig
        )(inputContext)
        .map(_.parameters)
        .valueOr { err =>
          logger.warn(
            s"Errors during inferring of initial parameters for component: $transformer: ${err.toList.mkString(", ")}. Will be used empty list of parameters as a fallback"
          )
          // It is better to return empty list than throw an exception. User will have an option to open the node, validate node again
          // and replace those parameters by the correct once
          List.empty
        }
    }

    component match {
      case withStatic: WithStaticParameters =>
        StandardParameterEnrichment.enrichParameterDefinitions(withStatic.staticParameters, parametersConfig)
      case single: SingleInputDynamicComponent[_] =>
        inferParameters(single)(ValidationContext())
      case join: JoinDynamicComponent[_] =>
        inferParameters(join)(Map.empty)
    }
  }

}

object DynamicComponentStaticDefinitionDeterminer {

  def collectStaticDefinitionsForDynamicComponents(
      modelDataForType: ModelData,
      createMetaData: ProcessName => MetaData
  ): Map[ComponentId, ComponentStaticDefinition] = {
    val nodeValidator = DynamicNodeValidator(modelDataForType)
    val toStaticComponentDefinitionTransformer =
      new DynamicComponentStaticDefinitionDeterminer(nodeValidator, createMetaData)

    // We have to wrap this block with model's class loader because it invokes node compilation under the hood
    modelDataForType.withThisAsContextClassLoader {
      modelDataForType.modelDefinition.components.collect {
        case dynamic: DynamicComponentDefinitionWithImplementation =>
          dynamic.id -> toStaticComponentDefinitionTransformer.determineStaticDefinition(dynamic)
      }.toMap
    }
  }

  def staticReturnType(component: DynamicComponent[_]): Option[TypingResult] = {
    if (component.nodeDependencies.contains(OutputVariableNameDependency)) Some(Unknown) else None
  }

}
