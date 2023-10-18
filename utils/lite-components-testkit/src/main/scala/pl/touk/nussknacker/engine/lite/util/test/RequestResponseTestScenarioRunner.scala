package pl.touk.nussknacker.engine.lite.util.test

import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import cats.Id
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, WithCategories}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter._
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseHttpHandler, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.util.test.{ModelWithTestComponents, TestScenarioRunner, TestScenarioRunnerBuilder, TestScenarioCollectorHandler}

import scala.reflect.ClassTag

object RequestResponseTestScenarioRunner {
  implicit class LiteKafkaTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def requestResponseBased(baseConfig: Config = ConfigFactory.load()): RequestResponseTestScenarioRunnerBuilder = {
      RequestResponseTestScenarioRunnerBuilder(Nil, Map.empty, baseConfig, testRuntimeMode = false)
    }
  }

  val stringFieldSchema: String = """{
                               |  "type": "object",
                               |  "properties": {
                               |    "field1": { "type": "string" }
                               |  }
                               |}
                               |""".stripMargin

  val sampleSchemas: Map[String, String] = Map("inputSchema" -> stringFieldSchema, "outputSchema" -> stringFieldSchema)

}

class RequestResponseTestScenarioRunner(components: List[ComponentDefinition], globalProcessVariables: Map[String, WithCategories[AnyRef]], config: Config, componentUseCase: ComponentUseCase)
    extends TestScenarioRunner {

  def runWithRequests[T](
    scenario: CanonicalProcess
  )(run: (HttpRequest => Either[NonEmptyList[ErrorType], Json]) => T): ValidatedNel[ProcessCompilationError, T] = {
    val testScenarioCollectorHandler = TestScenarioCollectorHandler.createHandler(componentUseCase)

    ModelWithTestComponents.withTestComponents(config, components, globalProcessVariables) { modelData =>
      RequestResponseInterpreter[Id](
        scenario,
        ProcessVersion.empty,
        LiteEngineRuntimeContextPreparer.noOp,
        modelData,
        additionalListeners = Nil,
        resultCollector = testScenarioCollectorHandler.resultCollector,
        componentUseCase = componentUseCase
      ).map { interpreter =>
        interpreter.open()
        try {
          val handler = new RequestResponseHttpHandler(interpreter)
          run(req => {
            val entity = req.entity.asInstanceOf[HttpEntity.Strict].data.toArray(implicitly[ClassTag[Byte]])
            handler.invoke(req, entity)
          })
        } finally {
          testScenarioCollectorHandler.close()
          interpreter.close()
        }
      }
    }
  }

}

case class RequestResponseTestScenarioRunnerBuilder(components: List[ComponentDefinition], globalProcessVariables: Map[String, WithCategories[AnyRef]], config: Config, testRuntimeMode: Boolean)
    extends TestScenarioRunnerBuilder[RequestResponseTestScenarioRunner, RequestResponseTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(extraComponents: List[ComponentDefinition]): RequestResponseTestScenarioRunnerBuilder =
    copy(components = extraComponents)

  override def inTestRuntimeMode: RequestResponseTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  override def withGlobalProcessVariables(globalProcessVariables: Map[String, WithCategories[AnyRef]]): RequestResponseTestScenarioRunnerBuilder =
    copy(globalProcessVariables = globalProcessVariables)

  override def build(): RequestResponseTestScenarioRunner =
    new RequestResponseTestScenarioRunner(components, globalProcessVariables, config, componentUseCase(testRuntimeMode))

}
