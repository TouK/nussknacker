package pl.touk.nussknacker.k8s.manager

import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.k8s.manager.KafkaK8sSupport.{kafkaServiceName, srServiceName}
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures
import skuber.api.client.KubernetesClient
import skuber.json.format._
import skuber.{Container, EnvVar, HTTPGetAction, ObjectMeta, Pod, Probe, Service}

import scala.concurrent.{ExecutionContext, Future, Promise}

object KafkaK8sSupport {

  val kafkaServiceName = "kafka-k8s-test"
  val srServiceName    = "sr-k8s-test"

}

//TODO: would it be faster if we run e.g. kcat as k8s job instead of exec into kafka pod?
class KafkaK8sSupport(k8s: KubernetesClient) extends ExtremelyPatientScalaFutures with LazyLogging with Matchers {

  private val k8sUtils = new K8sTestUtils(k8s)

  // set to false in development to reuse existing kafka pod
  private val cleanupKafka = true

  private val kafkaPodName = "kafka-k8s-test"
  private val srPodName    = "sr-k8s-test"

  def start()(implicit ec: ExecutionContext): Unit = if (k8s.getOption[Pod](kafkaPodName).futureValue.isEmpty) {
    val kafkaContainer = Container(
      name = kafkaPodName,
      // we use debezium image as it makes it easy to use kraft (KIP-500)
      // versions are not directly connected with Kafka versions
      image = "debezium/kafka:1.8",
      env = List(
        EnvVar("CLUSTER_ID", "5Yr1SIgYQz-b-dgRabWx4g"),
        EnvVar("BROKER_ID", "1"),
        EnvVar("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://localhost:9093"),
        EnvVar("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093"),
        EnvVar("ADVERTISED_KAFKA_LISTENERS", "PLAINTEXT://kafkaservice:9092")
      )
    )
    val kafkaPod = Pod(
      metadata = ObjectMeta(name = kafkaPodName, labels = Map("run" -> kafkaPodName)),
      spec = Some(Pod.Spec(containers = List(kafkaContainer)))
    )
    val kafkaService = Service(
      metadata = ObjectMeta(name = kafkaServiceName),
      spec = Some(
        Service.Spec(
          ports = List(Service.Port(port = 9092)),
          selector = Map("run" -> kafkaPodName)
        )
      )
    )

    val srContainer = Container(
      name = srPodName,
      // we use debezium image as it makes it easy to use kraft (KIP-500)
      // versions are not directly connected with Kafka versions
      image = "confluentinc/cp-schema-registry:7.2.1",
      env = List(
        EnvVar("SCHEMA_REGISTRY_HOST_NAME", "schemaregistry"),
        EnvVar("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", s"${KafkaK8sSupport.kafkaServiceName}:9092")
      ),
      readinessProbe = Some(
        Probe(new HTTPGetAction(Left(8081), path = "/subjects"), periodSeconds = Some(1), failureThreshold = Some(60))
      ),
      livenessProbe = Some(Probe(new HTTPGetAction(Left(8081), path = "/subjects")))
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

    def isContainerReady(podName: String) =
      k8s.get[Pod](podName).futureValue.status.get.containerStatuses.headOption.get.ready
    eventually {
      isContainerReady(kafkaPodName) shouldBe true
      isContainerReady(srPodName) shouldBe true
    }
  }

  def stop()(implicit ec: ExecutionContext): Unit = if (cleanupKafka) {
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

  def readFromTopic(name: String, count: Int): List[String] = {
    val command =
      s"/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $name --max-messages $count --from-beginning"
    val messages = k8sUtils.execPodWithLogs(kafkaPodName, command, _.lines.toArray.length == count, None)
    messages.split("\n").take(count).toList
  }

  private def runInKafka(command: String, end: String => Boolean, input: Option[String] = None): String =
    runInPod(kafkaPodName, command, end, input)

  private def runInPod(
      podName: String,
      command: String,
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
        command.split(" ").toIndexedSeq,
        maybeStdout = Some(sink),
        maybeStdin = inputSource,
        maybeClose = Some(close)
      )
      .futureValue
    output.toString()
  }

  def sendToTopic(name: String, value: String): Unit = {
    val command = s"/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $name"
    k8sUtils.execPodWithLogs(kafkaPodName, command, _ => true, Some(value))
  }

  def createTopic(name: String): Unit = {
    val command =
      s"/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic $name --partitions 1 --replication-factor 1"
    k8sUtils.execPodWithLogs(kafkaPodName, command, _.contains(s"Created topic $name"))
  }

  def createSchema(name: String, schema: String, schemaType: String = "JSON"): Unit = {
    val req     = s"""{"schemaType":"$schemaType","schema":"${schema.replace(""""""", """\"""")}"}"""
    val command = s"curl -v -H Content-Type:application/json localhost:8081/subjects/$name/versions -d $req"
    runInPod(srPodName, command, _.contains(""""id":"""))
  }

}
