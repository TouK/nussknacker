package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputDynamicComponent}
import pl.touk.nussknacker.engine.api.definition.{
  AdditionalVariableProvidedInRuntime,
  AdditionalVariableWithFixedValue,
  NodeDependency,
  Parameter
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.FragmentResolver
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationPerformed}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

class AdditionalVariableSpec extends AnyFunSuite with Matchers {

  test("detect AdditionalVariable with fixed value") {
    val parameters = definition(new CorrectService)
    parameters.head.additionalVariables shouldBe Map(
      "fixed" -> AdditionalVariableWithFixedValue(CanUseAsFixed(), Typed[CanUseAsFixed])
    )
    parameters.last.additionalVariables shouldBe Map("runtime" -> AdditionalVariableProvidedInRuntime[CannotUseAsFixed])
  }

  test("handles incorrect fixed value") {
    intercept[IllegalArgumentException] {
      definition(new IncorrectService1)
    }.getMessage shouldBe "Failed to create instance of pl.touk.nussknacker.engine.definition.CannotUseAsFixed, " +
      "it has to have no-arg constructor to be injected as AdditionalVariable"
  }

  test("doesn't allow LazyParameter with fixed value") {
    val modelData = LocalModelData(
      ConfigFactory.empty(),
      List(ComponentDefinition("one", new IncorrectService2))
    )
    val fragmentResolver = FragmentResolver(List.empty)
    val metaData         = MetaData("scenario", StreamMetaData())
    val jobData          = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    val result = new NodeDataValidator(modelData).validate(
      node.Source("sid", SourceRef("one", NodeParameter(ParameterName("toFail"), "''".spel) :: Nil)),
      ValidationContext.empty,
      Map.empty,
      Nil,
      fragmentResolver
    )(jobData)
    result.asInstanceOf[ValidationPerformed].errors.distinct shouldBe CannotCreateObjectError(
      "AdditionalVariableWithFixedValue should not be used with LazyParameters",
      "sid"
    ) :: Nil

  }

  private def definition(sourceFactory: SourceFactory): List[Parameter] = {
    ComponentDefinitionWithImplementation
      .withEmptyConfig("foo", sourceFactory)
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]
      .parameters
  }

  class CorrectService extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def invoke(
        @ParamName("fixed") @AdditionalVariables(
          Array(new AdditionalVariable(name = "fixed", clazz = classOf[CanUseAsFixed]))
        )
        eagerParam: String,
        @ParamName("runtime") @AdditionalVariables(
          Array(new AdditionalVariable(name = "runtime", clazz = classOf[CannotUseAsFixed]))
        )
        lazyParam: LazyParameter[String]
    ): Source = null

  }

  class IncorrectService1 extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def invoke(
        @ParamName("fixed") @AdditionalVariables(
          Array(new AdditionalVariable(name = "fixed", clazz = classOf[CannotUseAsFixed]))
        )
        eagerParam: String
    ): Source = null

  }

  class IncorrectService2 extends SourceFactory with SingleInputDynamicComponent[Source] with UnboundedStreamComponent {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
        implicit nodeId: NodeId
    ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
      NextParameters(
        List(
          Parameter[String](ParameterName("toFail"))
            .copy(
              isLazyParameter = true,
              additionalVariables = Map("failing" -> AdditionalVariableWithFixedValue("wontwork"))
            )
        )
      )
    }

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[Nothing]
    ): Source = null

    override def nodeDependencies: List[NodeDependency] = Nil

  }

}

case class CanUseAsFixed()

class CannotUseAsFixed(constructor: String)
