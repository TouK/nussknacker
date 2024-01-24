package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{
  ContextInitializer,
  ContextInitializingFunction,
  Source,
  TestWithParametersSupport
}
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.api.test.TestRecordParser
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.fragment.{
  FragmentComponentDefinition,
  FragmentParametersWithoutValidatorsDefinitionExtractor
}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkIntermediateRawSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition

// Needed to build source based on FragmentInputDefinition. It allows fragment to be treated as scenario (when it comes to testing)
// This source adds input parameters to context and allows testing with ad-hoc testing.
class StubbedFragmentSourceDefinitionPreparer(
    fragmentDefinitionExtractor: FragmentParametersWithoutValidatorsDefinitionExtractor
) {

  def createSourceDefinition(frag: FragmentInputDefinition): ComponentDefinitionWithImplementation = {
    val inputParameters = fragmentDefinitionExtractor.extractParametersDefinition(frag).value
    FragmentComponentDefinition(
      implementationInvoker = (_: Map[String, Any], _: Option[String], _: Seq[AnyRef]) => buildSource(inputParameters),
      // We don't want to pass input parameters definition as parameters to definition of factory creating stubbed source because
      // use them only for testParametersDefinition which are used in the runtime not in compile-time.
      parameters = List.empty,
      outputNames = List.empty,
      docsUrl = None,
      translateGroupName = Some(_),
      designerWideId = DesignerWideComponentId("dumpId"),
    )
  }

  private def buildSource(inputParameters: List[Parameter]): Source = {
    new Source
      with FlinkIntermediateRawSource[Map[String, Any]]
      with FlinkSourceTestSupport[Map[String, Any]]
      with TestWithParametersSupport[Map[String, Any]] {
      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Map[String, Any]]] = None

      override def typeInformation: TypeInformation[Map[String, Any]] = TypeInformation.of(classOf[Map[String, Any]])

      override def testRecordParser: TestRecordParser[Map[String, Any]] = ???

      override def timestampAssigner: Option[TimestampWatermarkHandler[Map[String, Any]]] = None

      override def testParametersDefinition: List[Parameter] = inputParameters

      override def parametersToTestData(params: Map[String, AnyRef]): Map[String, Any] = params

      override val contextInitializer: ContextInitializer[Map[String, Any]] = new ContextInitializer[Map[String, Any]] {

        override def initContext(
            contextIdGenerator: ContextIdGenerator
        ): ContextInitializingFunction[Map[String, Any]] = { input =>
          Context(contextIdGenerator.nextContextId(), input, None)
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
