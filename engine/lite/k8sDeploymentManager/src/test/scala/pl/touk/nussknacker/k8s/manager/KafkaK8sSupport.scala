package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.k8s.manager.KafkaK8sSupport.kafkaService
import pl.touk.nussknacker.test.ExtremelyPatientScalaFutures
import skuber.Container.Running
import skuber.api.client.KubernetesClient
import skuber.json.format._
import skuber.{Container, EnvVar, ObjectMeta, Pod, Service}

import scala.concurrent.{ExecutionContext, Future}

object KafkaK8sSupport {

  val kafkaService = "kafka-k8s-test"

}

//TODO: would it be faster if we run e.g. kcat as k8s job instead of exec into kafka pod?
class KafkaK8sSupport(k8s: KubernetesClient) extends ExtremelyPatientScalaFutures with LazyLogging with Matchers {

  private val k8sUtils = new K8sTestUtils(k8s)

  //set to false in development to reuse existing kafka pod
  private val cleanupKafka = true

  private val kafkaPod = "kafka-k8s-test"

  def start()(implicit ec: ExecutionContext): Unit = if (k8s.getOption[Pod](kafkaPod).futureValue.isEmpty) {
    val kafkaContainer = Container(
      name = kafkaPod,
      //we use debezium image as it makes it easy to use kraft (KIP-500)
      //versions are not directly connected with Kafka versions
      image = "debezium/kafka:1.8",
      env = List(
        EnvVar("CLUSTER_ID", "5Yr1SIgYQz-b-dgRabWx4g"),
        EnvVar("BROKER_ID", "1"),
        EnvVar("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://localhost:9093"),
        EnvVar("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9093"),
        EnvVar("ADVERTISED_KAFKA_LISTENERS", "PLAINTEXT://kafkaservice:9092")
      )
    )
    val pod = Pod(
      metadata = ObjectMeta(name = kafkaPod, labels = Map("run" -> kafkaPod)),
      spec = Some(Pod.Spec(containers = List(kafkaContainer)))
    )
    val service = Service(
      metadata = ObjectMeta(name = kafkaService),
      spec = Some(Service.Spec(
        ports = List(Service.Port(port = 9092)),
        selector = Map("run" -> kafkaPod)
      )))
    Future.sequence(List(k8s.create(pod), k8s.create(service))).futureValue
    eventually {
      val podStatus = k8s.get[Pod](kafkaPod).futureValue.status.get
      val containerState = podStatus.containerStatuses.headOption.get.state.get
      logger.debug(s"Container state: $containerState")
      containerState should matchPattern {
        case _: Running =>
      }
    }
  }

  def stop()(implicit ec: ExecutionContext): Unit = if (cleanupKafka) {
    Future.sequence(List(
      k8s.delete[Pod](kafkaPod, 1),
      k8s.delete[Service](kafkaService, 1)
    )).futureValue
    eventually {
      k8s.getOption[Pod](kafkaPod).futureValue shouldBe 'empty
      k8s.getOption[Service](kafkaService).futureValue shouldBe 'empty
    }
  }

  def readFromTopic(name: String, count: Int): List[String] = {
    val command = s"/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic $name --max-messages $count --from-beginning"
    val messages = k8sUtils.execPodWithLogs(kafkaPod, command, _.lines.toArray.length == count, None)
    messages.split("\n").take(count).toList
  }

  def sendToTopic(name: String, value: String): Unit = {
    val command = s"/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $name"
    k8sUtils.execPodWithLogs(kafkaPod, command, _ => true, Some(value))
  }

  def createTopic(name: String): Unit = {
    val command = s"/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic $name --partitions 1 --replication-factor 1"
    k8sUtils.execPodWithLogs(kafkaPod, command, _.contains(s"Created topic $name"))
  }

}
