package pl.touk.nussknacker.engine.requestresponse.test

import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.{FutureBasedRequestResponseScenarioInterpreter, RequestResponseConfigCreator, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.requestresponse.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

trait RunnableEspScenario extends Matchers with PatientScalaFutures {

  implicit class RunnableEspScenario(scenario: EspProcess)(implicit ec: ExecutionContext) {

    def runTestRunnerWith(input: String)(implicit modelData: ModelData): TestResults[Any]  = {
      FutureBasedRequestResponseScenarioInterpreter.testRunner.runTest(
        process = scenario,
        modelData = modelData,
        testData = new TestData(input.getBytes(StandardCharsets.UTF_8), 10), variableEncoder = identity)
    }

    val contextIdGenerator = IncContextIdGenerator.withProcessIdNodeIdPrefix(scenario.metaData, scenario.roots.head.id)

    def runTestWith(input: Any,
                    creator: RequestResponseConfigCreator = new RequestResponseConfigCreator,
                    metricRegistry: MetricRegistry = new MetricRegistry,
                    contextId: Option[String] = None): ValidatedNel[ErrorType, Any] =
      Using.resource(scenario.prepareInterpreter(
        creator = creator,
        metricRegistry = metricRegistry
      )) { interpreter =>
        interpreter.open()
        invokeInterpreter(interpreter, input)
      }

    def prepareInterpreter(creator: RequestResponseConfigCreator,
                           metricRegistry: MetricRegistry)(implicit ec: ExecutionContext): InterpreterType = {
      scenario.prepareInterpreter(creator, new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry)))
    }

    def prepareInterpreter(creator: RequestResponseConfigCreator = new RequestResponseConfigCreator,
                           engineRuntimeContextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp)(implicit ec: ExecutionContext): InterpreterType = {
      val simpleModelData = LocalModelData(ConfigFactory.load(), creator)

      import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
      val maybeinterpreter = RequestResponseInterpreter[Future](scenario, ProcessVersion.empty,
        engineRuntimeContextPreparer, simpleModelData, Nil, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)

      maybeinterpreter shouldBe 'valid
      val interpreter = maybeinterpreter.toOption.get
      interpreter
    }

  }

  private[requestresponse] def invokeInterpreter(interpreter: InterpreterType, input: Any)(implicit ec: ExecutionContext) = {
    val metrics = new InvocationMetrics(interpreter.context)
    metrics.measureTime {
      interpreter.invokeToOutput(input)
    }.futureValue
  }

}

