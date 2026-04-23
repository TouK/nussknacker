package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.{KafkaClient, RichKafkaConsumer}
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures
import skuber.{Container, EnvVar, HTTPGetAction, ObjectMeta, Pod, Probe, Protocol, Service, TCPSocketAction}
import skuber.api.client.KubernetesClient
import skuber.json.format._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Using
import scala.util.control.NonFatal

object KafkaK8sSupport {

  val kafkaServiceName = "kafka-k8s-test"
  val srServiceName    = "sr-k8s-test"

}

class KafkaK8sSupport(k8s: KubernetesClient)(private implicit val system: ActorSystem)
    extends ExtremelyPatientScalaFutures
    with LazyLogging
    with Matchers {

  import pl.touk.nussknacker.k8s.manager.KafkaK8sSupport._

  private val k8sUtils = new K8sTestUtils(k8s)

  private val kafkaPodName        = "kafka-k8s-test"
  private val kafkaPodExposedPort = 30092
  private val srPodName           = "sr-k8s-test"

  private var kafkaPortForwarder: K8sPortForwarder = _

  private val kafkaClient =
    new KafkaClient(s"${k8sUtils.portForwardHost}:$kafkaPodExposedPort", getClass.getSimpleName)

  def start()(implicit ec: ExecutionContext): Unit = if (k8s.getOption[Pod](kafkaPodName).futureValue.isEmpty) {
    val kafkaContainer = Container(
      name = kafkaPodName,
      image = "apache/kafka-native:4.1.2",
      ports = List(
        Container.Port(9092, Protocol.TCP, "kafka-internal"),
        Container.Port(kafkaPodExposedPort, Protocol.TCP, "kafka-localhost"),
      ),
      env = List(
        EnvVar("KAFKA_NODE_ID", "1"),
        EnvVar("KAFKA_PROCESS_ROLES", "broker,controller"),
        EnvVar("KAFKA_LISTENERS", s"BROKER://:9092,CONTROLLER://localhost:9093,LOCALHOST://:$kafkaPodExposedPort"),
        EnvVar(
          "KAFKA_ADVERTISED_LISTENERS",
          s"BROKER://$kafkaServiceName:9092,CONTROLLER://localhost:9093,LOCALHOST://${k8sUtils.portForwardHost}:$kafkaPodExposedPort"
        ),
        EnvVar("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER"),
        EnvVar("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER"),
        EnvVar("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT,LOCALHOST:PLAINTEXT"),
        EnvVar("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093"),
        EnvVar("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1"),
        EnvVar("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1"),
        EnvVar("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"),
        EnvVar("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0"),
      ),
      readinessProbe = Some(
        Probe(TCPSocketAction(Left(9092)))
      ),
    )
    val kafkaPod = Pod(
      metadata = ObjectMeta(name = kafkaPodName, labels = Map("run" -> kafkaPodName)),
      spec = Some(Pod.Spec(containers = List(kafkaContainer)))
    )
    val kafkaService = Service(
      metadata = ObjectMeta(name = kafkaServiceName),
      spec = Some(
        Service.Spec(
          _type = Service.Type.NodePort,
          ports = List(
            Service.Port(port = 9092, name = "internal"), // will get an auto-assigned nodePort
            Service.Port(port = kafkaPodExposedPort, name = "localhost", nodePort = kafkaPodExposedPort)
          ),
          selector = Map("run" -> kafkaPodName)
        )
      )
    )

    val srContainer = Container(
      name = srPodName,
      image = "ghcr.io/axonops/axonops-schema-registry:0.2.1",
      readinessProbe = Some(Probe(new HTTPGetAction(Left(8081), path = "/health/ready"))),
    )
    val srPod = Pod(
      metadata = ObjectMeta(name = srPodName, labels = Map("run" -> srPodName)),
      spec = Some(Pod.Spec(containers = List(srContainer)))
    )
    val srService = Service(
      metadata = ObjectMeta(name = srServiceName),
      spec = Some(
        Service.Spec(
          ports = List(Service.Port(port = 8081)),
          selector = Map("run" -> srPodName)
        )
      )
    )

    Future
      .sequence(List(k8s.create(kafkaPod), k8s.create(kafkaService), k8s.create(srPod), k8s.create(srService)))
      .futureValue

    def isContainerDone(podName: String, allowFailed: Boolean = true) = {
      val status = k8s.get[Pod](podName).futureValue.status.get.containerStatuses.headOption.get
      status.ready || (allowFailed && status.restartCount > 0)
    }

    try {
      eventually {
        isContainerDone(kafkaPodName) shouldBe true
        isContainerDone(srPodName) shouldBe true
      }
      isContainerDone(kafkaPodName, allowFailed = false) shouldBe true
      isContainerDone(srPodName, allowFailed = false) shouldBe true
    } catch {
      case NonFatal(e) =>
        k8sUtils.printResourcesDetails()
        throw e
    }

    kafkaPortForwarder =
      new K8sPortForwarder(kafkaPod, kafkaPodExposedPort, k8sUtils.portForwardHost, kafkaPodExposedPort).start()
  }

  def stop()(implicit ec: ExecutionContext): Unit = {
    kafkaClient.shutdown()
    if (kafkaPortForwarder != null) {
      kafkaPortForwarder.close()
    }
    Future
      .sequence(
        List(
          k8sUtils.deleteIfExists[Pod](kafkaPodName, 1),
          k8sUtils.deleteIfExists[Pod](srPodName, 1),
          k8sUtils.deleteIfExists[Service](kafkaServiceName, 1),
          k8sUtils.deleteIfExists[Service](srServiceName, 1)
        )
      )
      .futureValue
    eventually {
      k8s.getOption[Pod](kafkaPodName).futureValue shouldBe Symbol("empty")
      k8s.getOption[Pod](srPodName).futureValue shouldBe Symbol("empty")
      k8s.getOption[Service](kafkaServiceName).futureValue shouldBe Symbol("empty")
      k8s.getOption[Service](srServiceName).futureValue shouldBe Symbol("empty")
    }
  }

  def readFromTopic(name: String, count: Int, secondsToWait: Int = 5): List[String] = {
    Using.resource(kafkaClient.createConsumer()) { consumer =>
      new RichKafkaConsumer(consumer)
        .consumeWithConsumerRecord(name, secondsToWait)
        .map { record =>
          logger.info(
            s"Read Kafka message: ${record.topic()}:${record.partition()}:${record.offset()} = ${new String(record.value())}"
          )
          new String(record.value())
        }
        .take(count)
        .toList
    }
  }

  private def runInPod(
      podName: String,
      command: Seq[String],
      end: String => Boolean,
      input: Option[String] = None
  ): String = {
    val close  = Promise[Unit]()
    val output = new StringBuilder
    val sink: Sink[String, Future[Done]] = Sink.foreach { s =>
      logger.debug(s"[pod: $podName] received: $s")
      output.append(s)
      if (end(output.toString())) {
        close.success(())
      }
    }
    val inputSource = input.map(Source.single)
    k8s
      .exec(
        podName,
        command,
        maybeStdout = Some(sink),
        maybeStdin = inputSource,
        maybeClose = Some(close)
      )
      .futureValue
    output.toString()
  }

  def sendToTopic(name: String, value: String): Unit = {
    kafkaClient.sendMessage(name, value).futureValue
  }

  def createTopic(name: String): Unit = {
    kafkaClient.createTopic(name, 1)
  }

  def createSchema(name: String, schema: String, schemaType: String = "JSON"): Unit = {
    val req = s"""{"schemaType":"$schemaType","schema":"${schema.replace(""""""", """\"""")}"}"""
    val command = Seq(
      "wget",
      "--header",
      "Content-Type: application/json",
      "--post-data",
      req,
      "-O",
      "-",
      s"localhost:8081/subjects/$name/versions"
    )
    runInPod(srPodName, command, _.contains(""""id":"""))
  }

}
