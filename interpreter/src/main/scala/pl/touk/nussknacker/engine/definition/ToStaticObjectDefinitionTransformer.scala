package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{GenericNodeTransformation, JoinGenericNodeTransformation, SingleInputGenericNodeTransformation, WithStaticParameters}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, ScenarioSpecificData}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.GenericNodeTransformationValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition.parameter.StandardParameterEnrichment

// This class purpose is to provide initial set of parameters that will be presented after first usage of a component.
// It is necessary to provide them, because:
// - We want to avoid flickering of parameters after first entering into the node
// - Sometimes user want to just use the component without filling parameters with own data - in this case we want to make sure
//   that parameters will be available in the scenario, even with a default values
class ToStaticObjectDefinitionTransformer(objectParametersExpressionCompiler: ExpressionCompiler,
                                          expressionConfig: ExpressionDefinition[ObjectWithMethodDef],
                                          createMetaData: ProcessName => MetaData) extends LazyLogging {

  private val nodeValidator = new GenericNodeTransformationValidator(objectParametersExpressionCompiler, expressionConfig)

  def toStaticObjectDefinition(objectWithMethodDef: ObjectWithMethodDef): ObjectDefinition = {
    objectWithMethodDef match {
      case standard: StandardObjectWithMethodDef => standard.objectDefinition
      case generic: GenericNodeTransformationMethodDef =>
        val parameters = determineInitialParameters(generic)
        ObjectDefinition(parameters, generic.returnType, generic.categories, generic.componentConfig)
    }
  }

  private def determineInitialParameters(generic: GenericNodeTransformationMethodDef): List[Parameter] = {
    def inferParameters(transformer: GenericNodeTransformation[_])(inputContext: transformer.InputContext) = {
      // TODO: We could determine initial parameters when component is firstly used in scenario instead of during loading model data
      //       Thanks to that, instead of passing fake nodeId/metaData and empty additionalFields, we could pass the real once
      val scenarioName = ProcessName("fakeScenarioName")
      implicit val metaData: MetaData = createMetaData(scenarioName)
      implicit val nodeId: NodeId = NodeId("fakeNodeId")
      nodeValidator
        .validateNode(transformer, Nil, Nil, generic.returnType.map(_ => "fakeOutputVariable"), generic.componentConfig)(inputContext)
        .map(_.parameters).valueOr { err =>
          logger.warn(s"Errors during inferring of initial parameters for component: $transformer: ${err.toList.mkString(", ")}. Will be used empty list of parameters as a fallback")
          // It is better to return empty list than throw an exception. User will have an option to open the node, validate node again
          // and replace those parameters by the correct once
          List.empty
        }
    }

    generic.obj match {
      case withStatic: WithStaticParameters =>
        StandardParameterEnrichment.enrichParameterDefinitions(withStatic.staticParameters, generic.componentConfig)
      case single: SingleInputGenericNodeTransformation[_] =>
        inferParameters(single)(ValidationContext())
      case join: JoinGenericNodeTransformation[_] =>
        inferParameters(join)(Map.empty)
    }
  }

}
