package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromMap}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import scala.jdk.CollectionConverters._

class LiteKafkaComponentProviderSpec extends AnyFunSuite with MockitoSugar with Matchers {

  val schemaRegistryClientFactory: SchemaRegistryClientFactory = mock[SchemaRegistryClientFactory]
  val sut: LiteKafkaComponentProvider = new LiteKafkaComponentProvider(schemaRegistryClientFactory)

  test("should create kafka components from configuration") {
    val emptyConfig = ConfigFactory.empty()
    val result      = sut.create(emptyConfig, ProcessObjectDependencies.withConfig(emptyConfig))

    result.map(_.name).distinct shouldBe List("kafka")
    result.map(_.component.getClass.getSimpleName) shouldBe
      List("UniversalKafkaSourceFactory", "UniversalKafkaSinkFactory")
  }

  test("should throw exception if idleness is passed in configuration") {
    val config = ConfigFactory
      .empty()
      .withValue("kafka.idleTimeout", fromMap(Map("enabled" -> "true", "duration" -> "3 seconds").asJava))

    val ex = intercept[IllegalArgumentException](sut.create(config, ProcessObjectDependencies.withConfig(config)))

    ex.getMessage shouldBe "Idleness is a Flink specific feature and is not supported in Lite Kafka sources. " +
      "Please remove the idleness config from your Lite Kafka sources config."
  }

  test("should throw exception if sinkDeliveryGuarantee is passed in configuration") {
    val config = ConfigFactory
      .empty()
      .withValue("kafka.sinkDeliveryGuarantee", fromAnyRef("EXACTLY_ONCE"))

    val ex = intercept[IllegalArgumentException](sut.create(config, ProcessObjectDependencies.withConfig(config)))

    ex.getMessage shouldBe "SinkDeliveryGuarantee is a Flink specific feature and is not supported in Lite Kafka " +
      "config. Please remove the sinkDeliveryGuarantee property from your Lite Kafka config."
  }

}
