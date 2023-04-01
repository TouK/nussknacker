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
object ToStaticObjectDefinitionTransformer {

  def toStaticObjectDefinition(objectWithMethodDef: ObjectWithMethodDef): ObjectDefinition = {
    objectWithMethodDef match {
      case standard: StandardObjectWithMethodDef => standard.objectDefinition
      case generic: GenericNodeTransformationMethodDef =>
        val parameters = determineInitialParameters(generic)
        ObjectDefinition(parameters, generic.returnType, generic.categories, generic.componentConfig)
    }
  }

  private def determineInitialParameters(generic: GenericNodeTransformationMethodDef): List[Parameter] = {
    generic.obj match {
      case withStatic: WithStaticParameters =>
        StandardParameterEnrichment.enrichParameterDefinitions(withStatic.staticParameters, generic.componentConfig)
      case j: JoinGenericNodeTransformation[_] =>
        // TODO: currently branch parameters must be determined on node template level - aren't enriched dynamically during node validation
        StandardParameterEnrichment.enrichParameterDefinitions(j.initialBranchParameters, generic.componentConfig)
      case _ =>
        List.empty
    }
  }

}
