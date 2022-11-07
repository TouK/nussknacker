package pl.touk.nussknacker.engine.lite.util.test

import akka.http.scaladsl.model.{HttpEntity, HttpRequest}
import cats.Id
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ComponentUseCase.EngineRuntime
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter._
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseHttpHandler, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.test.{ModelWithTestComponents, TestScenarioRunner, TestScenarioRunnerBuilder}

import scala.reflect.ClassTag

object RequestResponseTestScenarioRunner {
  implicit class LiteKafkaTestScenarioRunnerExt(testScenarioRunner: TestScenarioRunner.type) {

    def requestResponseBased(baseConfig: Config = ConfigFactory.load()): RequestResponseTestScenarioRunnerBuilder = {
      RequestResponseTestScenarioRunnerBuilder(Nil, baseConfig)
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

class RequestResponseTestScenarioRunner(components: List[ComponentDefinition], config: Config)
    extends TestScenarioRunner {

  def runWithRequests[T](
    scenario: CanonicalProcess
  )(run: (HttpRequest => Either[NonEmptyList[ErrorType], Json]) => T): ValidatedNel[ProcessCompilationError, T] = {
    ModelWithTestComponents.withTestComponents(config, components) { modelData =>
      RequestResponseInterpreter[Id](
        scenario,
        ProcessVersion.empty,
        LiteEngineRuntimeContextPreparer.noOp,
        modelData,
        additionalListeners = Nil,
        resultCollector = ProductionServiceInvocationCollector,
        componentUseCase = EngineRuntime
      ).map { interpreter =>
        interpreter.open()
        try {
          val handler = new RequestResponseHttpHandler(interpreter)
          run(req => {
            val entity = req.entity.asInstanceOf[HttpEntity.Strict].data.toArray(implicitly[ClassTag[Byte]])
            handler.invoke(req, entity)
          })
        } finally {
          interpreter.close()
        }
      }
    }
  }

}

case class RequestResponseTestScenarioRunnerBuilder(extraComponents: List[ComponentDefinition], config: Config)
    extends TestScenarioRunnerBuilder[RequestResponseTestScenarioRunner, RequestResponseTestScenarioRunnerBuilder] {

  override def withExtraComponents(
    extraComponents: List[ComponentDefinition]
  ): RequestResponseTestScenarioRunnerBuilder =
    copy(extraComponents = extraComponents)

  override def build(): RequestResponseTestScenarioRunner =
    new RequestResponseTestScenarioRunner(extraComponents, config)

}
