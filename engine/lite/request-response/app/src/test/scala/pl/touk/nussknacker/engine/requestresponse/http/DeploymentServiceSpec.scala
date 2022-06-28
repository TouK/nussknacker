package pl.touk.nussknacker.engine.requestresponse.http

import com.typesafe.config.ConfigFactory
import io.circe.generic.JsonCodec
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName, ProcessVersion}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName, ProcessObjectDependencies, Sink, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.requestresponse.deployment.{FileProcessRepository, RequestResponseDeploymentData}
import pl.touk.nussknacker.engine.requestresponse.utils.JsonRequestResponseSourceFactory
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.nio.file.Files

class DeploymentServiceSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val tmpDir = Files.createTempDirectory("deploymentSpec")

  def createService() = new DeploymentService(LiteEngineRuntimeContextPreparer.noOp,
    LocalModelData(ConfigFactory.load(), TestRequestResponseConfigCreator),
    new FileProcessRepository(tmpDir.toFile))

  it should "preserve processes between deployments" in {

    val id = ProcessName("process1")
    val json = processWithIdAndPath(id, None)

    var service = createService()

    def restartApp(): Unit = {
      service = createService()
    }

    service.deploy(json).right.toOption shouldBe 'defined

    service.checkStatus(id) shouldBe 'defined

    restartApp()

    service.checkStatus(id) shouldBe 'defined
    service.cancel(id)
    service.checkStatus(id) shouldBe 'empty

    restartApp()
    service.checkStatus(id) shouldBe 'empty
  }

  it should "deploy on given path" in {
    val id1 = ProcessName("process1")
    val id2 = ProcessName("process2")
    val id3 = ProcessName("alamakota")

    val service = createService()

    service.deploy(processWithIdAndPath(id1, None))  shouldBe 'right
    service.getInterpreterHandlerByPath(id1.value) shouldBe 'defined
    service.checkStatus(id1) shouldBe 'defined

    service.deploy(processWithIdAndPath(id1, Some("alamakota"))) shouldBe 'right
    service.getInterpreterHandlerByPath("process1") shouldBe 'empty
    service.getInterpreterHandlerByPath("alamakota") shouldBe 'defined
    service.checkStatus(id1) shouldBe 'defined
    service.checkStatus(id3) shouldBe 'empty
  }

  it should "not allow deployment on same path" in {
    val id = ProcessName("process1")
    val id2 = ProcessName("process2")

    val service = createService()

    service.deploy(processWithIdAndPath(id, None))

    service.deploy(processWithIdAndPath(id2, Some("process1"))) shouldBe 'left

    service.deploy(processWithIdAndPath(id, Some("alamakota"))) shouldBe 'right

    service.deploy(processWithIdAndPath(id2, Some("process1"))) shouldBe 'right

  }

  private def processWithIdAndPath(processName: ProcessName, path: Option[String]) = {
    val canonical = ScenarioBuilder
        .requestResponse(processName.value)
        .path(path)
        .source("start", "request1-post-source")
        .emptySink("endNodeIID", "response-sink", "value" -> "''")
        .toCanonicalProcess
    RequestResponseDeploymentData(canonical, 0, ProcessVersion.empty.copy(processName = processName), DeploymentData.empty)
  }

  object TestRequestResponseConfigCreator extends EmptyProcessConfigCreator {

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
      "request1-post-source" -> WithCategories(new JsonRequestResponseSourceFactory[Request1])
    )

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
      "response-sink" -> WithCategories(RequestResponseSinkFactory),
    )

    @JsonCodec case class Request1(field1: String, field2: String)

    private object RequestResponseSinkFactory extends SinkFactory {

      @MethodToInvoke
      def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new LazyParamSink[AnyRef] {
        override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = value
      }

    }
  }

}
