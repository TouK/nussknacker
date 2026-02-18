package pl.touk.nussknacker.engine.process.scenariotesting

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.TestRecordParser
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentIcon.FragmentIcon
import pl.touk.nussknacker.engine.definition.fragment.{
  FragmentComponentDefinition,
  FragmentParametersDefinitionExtractor
}
import pl.touk.nussknacker.engine.flink.api.process.{CustomizableContextInitializerSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition

// Needed to build source based on FragmentInputDefinition. It allows fragment to be treated as scenario (when it comes to testing)
// This source adds input parameters to context and allows testing with ad-hoc testing.
class StubbedFragmentSourceDefinitionPreparer(
    fragmentDefinitionExtractor: FragmentParametersDefinitionExtractor,
    parameterExpressionsFromTestData: Map[ParameterName, Expression],
) {

  def createSourceDefinition(name: String, frag: FragmentInputDefinition): ComponentDefinitionWithImplementation = {
    val inputParameters = fragmentDefinitionExtractor.extractParametersDefinition(frag).value.map { parameter =>
      parameterExpressionsFromTestData.get(parameter.name) match {
        case Some(valueFromTestData) => parameter.copy(defaultValue = Some(valueFromTestData))
        case None                    => parameter
      }
    }
    FragmentComponentDefinition(
      name = name,
      implementationInvoker =
        (_: Params, _: NodeCompilationDependencies, _: Option[ComponentImplementationSpecificInvocationContext]) =>
          buildSource(inputParameters),
      parameters = inputParameters,
      outputNames = List.empty,
      docsUrl = None,
      componentGroupName = None,
      icon = Some(FragmentIcon),
      translateGroupName = Some(_),
      designerWideId = DesignerWideComponentId("dumbId"),
      allowedProcessingModes = AllowedProcessingModes.All,
    )
  }

  private def buildSource(inputParameters: List[Parameter]): Source = {
    new Source
      with CustomizableContextInitializerSource[Map[String, Any]]
      with FlinkSourceTestSupport[Map[String, Any]]
      with TestWithParametersSupport[Map[String, Any]] {
      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Map[String, Any]]] = None

      override def testRecordParser: TestRecordParser[Map[String, Any]] = ???

      override def testParametersDefinition: List[Parameter] = inputParameters

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): Map[String, Any] =
        params.map { case (k, v) => (k.value, v) }

      override val contextInitializer: ContextInitializer[Map[String, Any]] =
        new ContextInitializer[Map[String, Any]] {

          override def convertToInitialVariables(input: Map[String, Any]): ContextVariables = {
            ContextVariables(input)
          }

          override def validationContext(
              context: ValidationContext
          )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
            Valid(context)
          }
        }
    }
  }

}
