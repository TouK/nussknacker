package pl.touk.nussknacker.engine.management.streaming

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{NodeResult, ResultContext, TestData}
import pl.touk.nussknacker.engine.api.deployment.{ProcessingTypeDeploymentService, ProcessingTypeDeploymentServiceStub}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.seqAsJavaListConverter

class FlinkStreamingProcessTestRunnerSpec extends FlatSpec with Matchers with VeryPatientScalaFutures {

  private implicit val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  import actorSystem.dispatcher
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  implicit val deploymentService: ProcessingTypeDeploymentService = new ProcessingTypeDeploymentServiceStub(List.empty)

  private val classPath: List[String] = ClassPaths.scalaClasspath

  private val config = ConfigFactory.load()
    .withValue("deploymentConfig.restUrl", fromAnyRef(s"http://dummy:1234"))
    .withValue("modelConfig.kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:1234"))
    .withValue("modelConfig.classPath", ConfigValueFactory.fromIterable(classPath.asJava))

  it should "run scenario in test mode" in {
    val deploymentManager = FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(config)

    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2

    whenReady(deploymentManager.test(ProcessName(processId), processData, TestData.newLineSeparated("terefere"), identity)) { r =>
      r.nodeResults shouldBe Map(
        "startProcess" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
        "nightFilter" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
        "endSend" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere"))))
      )
    }
  }

  it should "return correct error messages" in {
    val processId = UUID.randomUUID().toString

    val process = EspProcessBuilder
      .id(processId)
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSmsNotExist")

    val deploymentManager = FlinkStreamingDeploymentManagerProvider.defaultDeploymentManager(config)

    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2


    val caught = intercept[IllegalArgumentException] {
      Await.result(deploymentManager.test(ProcessName(processId), processData, TestData.newLineSeparated("terefere"), _ => null), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingSinkFactory(sendSmsNotExist,endSend)"
  }

}
