package pl.touk.nussknacker.k8s.manager

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, DoNotDiscover, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import skuber.api.client.KubernetesClient
import skuber.k8sInit

import scala.language.reflectiveCalls

/**
 * Tests for use for testing [[KafkaK8sSupport]], should be run manually.
 */
@DoNotDiscover
class KafkaK8sSupportTests
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with OptionValues
    with PatientScalaFutures
    with EitherValuesDetailedMessage
    with LazyLogging {

  import system.dispatcher

  private implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName)

  private lazy val k8s: KubernetesClient = k8sInit(system)
  private lazy val k8sTestUtils          = new K8sTestUtils(k8s)

  private lazy val kafka = new KafkaK8sSupport(k8s)

  override protected def beforeAll(): Unit = {
    kafka.start()
    k8sTestUtils.printResourcesDetails()
  }

  override protected def afterAll(): Unit = {
    kafka.stop()
  }

  test("Kafka container") {
    kafka.createTopic("k8s-test-topic")
    kafka.sendToTopic("k8s-test-topic", "test1")
    kafka.readFromTopic("k8s-test-topic", 1) shouldBe List("test1")
    kafka.sendToTopic("k8s-test-topic", "test2")
    kafka.readFromTopic("k8s-test-topic", 2) shouldBe List("test1", "test2")
  }

  test("Schema Registry container") {
    kafka.createSchema("k8s-test-topic-subject", "{}")
  }

}
