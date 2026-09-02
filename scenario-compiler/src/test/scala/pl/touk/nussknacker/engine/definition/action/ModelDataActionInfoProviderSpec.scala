package pl.touk.nussknacker.engine.definition.action

import cats.effect.SyncIO
import cats.effect.kernel.Resource
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, EagerService, MethodToInvoke, ProcessVersion, ServiceInvoker}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, StaticParameterConfig}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ComponentUseContext, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compile.validationHelpers.{SimpleStreamTransformer, SimpleStringSource}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.concurrent.{ExecutionContext, Future}

class ModelDataActionInfoProviderSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage {

  private val testAction = ScenarioActionName.RunOffSchedule

  private val actionParams: Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]] =
    Map(
      testAction -> Map(
        ParameterName("mockValue") -> StaticParameterConfig(
          defaultValue = None,
          validators = None,
          label = Some("Mock value"),
          hintText = None
        )
      )
    )

  /** `compileAllCustomNodes` collects the service invoker of every Processor/Enricher node, so the marker sits
    * on the invoker.
    */
  object ActionAwareService extends EagerService {

    @MethodToInvoke
    def prepare(): ServiceInvoker = new ServiceInvoker with WithActionParametersSupport {

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseContext: ComponentUseContext,
      ): Future[Any] = Future.successful(null)

      override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]] =
        actionParams
    }

  }

  test("collects action parameters of a service sitting in an additional output's branch") {
    val scenario = ScenarioBuilder
      .streaming("action-params")
      .source("source", "mySource")
      .customNodeWithOutputs(
        "multi",
        Some("outVar"),
        "myCustomStreamTransformer",
        List(
          "main" -> GraphBuilder.emptySink("mainSink", "dummySink").value,
          "rejected" -> GraphBuilder
            .processor("reject-action", "actionService")
            .emptySink("rejectedSink", "dummySink")
            .value
        ),
        "stringVal" -> "'x'".spel
      )

    val modelData = LocalModelData(
      ConfigFactory.empty(),
      List(
        ComponentDefinition("mySource", SimpleStringSource),
        ComponentDefinition("dummySink", SinkFactory.noParam(new Sink {})),
        ComponentDefinition("myCustomStreamTransformer", SimpleStreamTransformer),
        ComponentDefinition("actionService", ActionAwareService)
      )
    )
    val provider = new ModelDataActionInfoProvider(
      modelData,
      Resource.pure[SyncIO, EngineScenarioCompilationDependencies](EngineScenarioCompilationDependencies.empty)
    )

    val params = provider
      .getActionParameters(ProcessVersion.empty.copy(processName = scenario.name), scenario)
      .validValue

    params(testAction).keys.map(_.nodeId) should contain("reject-action")
    params(testAction).values.head shouldBe actionParams(testAction)
  }

}
