package pl.touk.nussknacker.engine.lite.util.test

import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpRequest}
import cats.Id
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import org.everit.json.schema.TrueSchema
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.lite.components.requestresponse.RequestResponseComponentProvider
import pl.touk.nussknacker.engine.lite.util.test.SynchronousLiteInterpreter._
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseHttpHandler, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.util.test.{
  TestScenarioCollectorHandler,
  TestScenarioRunner,
  TestScenarioRunnerBuilder
}

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

  val trueFieldSchema: String = TrueSchema.builder().build().toString

  val sampleSchemas: Map[String, String] = Map("inputSchema" -> stringFieldSchema, "outputSchema" -> stringFieldSchema)

}

class RequestResponseTestScenarioRunner(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    componentUseCase: ComponentUseCase
) extends TestScenarioRunner {

  def runWithRequests[T](
      scenario: CanonicalProcess
  )(run: (HttpRequest => Either[NonEmptyList[ErrorType], Json]) => T): ValidatedNel[ProcessCompilationError, T] = {
    TestScenarioCollectorHandler.withHandler(componentUseCase) { testScenarioCollectorHandler =>
      val modelData = ModelWithTestExtensions(
        config,
        LiteBaseComponentProvider.Components :::
          RequestResponseComponentProvider.Components :::
          components,
        globalVariables
      )
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
          interpreter.close()
        }
      }
    }
  }

}

case class RequestResponseTestScenarioRunnerBuilder(
    components: List[ComponentDefinition],
    globalVariables: Map[String, AnyRef],
    config: Config,
    testRuntimeMode: Boolean
) extends TestScenarioRunnerBuilder[RequestResponseTestScenarioRunner, RequestResponseTestScenarioRunnerBuilder] {

  import TestScenarioRunner._

  override def withExtraComponents(
      components: List[ComponentDefinition]
  ): RequestResponseTestScenarioRunnerBuilder =
    copy(components = components)

  override def withExtraGlobalVariables(
      globalVariables: Map[String, AnyRef]
  ): RequestResponseTestScenarioRunnerBuilder =
    copy(globalVariables = globalVariables)

  override def inTestRuntimeMode: RequestResponseTestScenarioRunnerBuilder =
    copy(testRuntimeMode = true)

  override def build(): RequestResponseTestScenarioRunner =
    new RequestResponseTestScenarioRunner(components, globalVariables, config, componentUseCase(testRuntimeMode))

}
