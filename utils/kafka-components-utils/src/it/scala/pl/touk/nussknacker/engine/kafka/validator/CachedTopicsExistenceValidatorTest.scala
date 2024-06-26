package pl.touk.nussknacker.engine.kafka.validator

import com.dimafeng.testcontainers.{ForAllTestContainer, ForEachTestContainer, KafkaContainer}
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.validator.TopicsExistenceValidationConfigForTest.createKafkaContainer
import pl.touk.nussknacker.engine.kafka.{
  CachedTopicsExistenceValidatorConfig,
  KafkaConfig,
  KafkaUtils,
  TopicsExistenceValidationConfig
}

import java.util.Collections
import scala.concurrent.duration.DurationInt

object TopicsExistenceValidationConfigForTest {

  val config: TopicsExistenceValidationConfig = {
    // longer timeout, as container might need some time to make initial assignments etc.
    TopicsExistenceValidationConfig(
      enabled = true,
      validatorConfig = CachedTopicsExistenceValidatorConfig.DefaultConfig.copy(adminClientTimeout = 5 seconds)
    )
  }

  def createKafkaContainer(): KafkaContainer =
    KafkaContainer(DockerImageName.parse(s"${KafkaContainer.defaultImage}:7.4.0"))
}

class CachedTopicsExistenceValidatorWhenAutoCreateDisabledTest
    extends AnyFunSuite
    with ForAllTestContainer
    with Matchers {

  override val container: KafkaContainer =
    createKafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "FALSE"))

  private def kafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> container.bootstrapServers)),
    kafkaEspProperties = None,
    consumerGroupNamingStrategy = None,
    avroKryoGenericRecordSchemaIdSerialization = None,
    topicsExistenceValidationConfig = TopicsExistenceValidationConfigForTest.config
  )

  test("should validate existing topic") {
    val topic = new NewTopic("test.topic.1", Collections.emptyMap())
    KafkaUtils.usingAdminClient(kafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](topic))
    }
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(TopicName.OfSource(topic.name())) shouldBe Symbol("valid")
  }

  test("should validate not existing topic") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(TopicName.OfSource("not.existing")) shouldBe Symbol("invalid")
  }

  test("should not validate not existing topic when validation disabled") {
    val v = new CachedTopicsExistenceValidator(
      kafkaConfig.copy(
        kafkaProperties = Some(Map("bootstrap.servers" -> "broken address")),
        topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)
      )
    )
    v.validateTopic(TopicName.OfSource("not.existing")) shouldBe Symbol("valid")
  }

  test("should fetch topics every time when not valid using cache") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(TopicName.OfSource("test.topic.2")) shouldBe Symbol("invalid")

    KafkaUtils.usingAdminClient(kafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](new NewTopic("test.topic.2", Collections.emptyMap())))
    }

    v.validateTopic(TopicName.OfSource("test.topic.2")) shouldBe Symbol("valid")
  }

}

class CachedTopicsExistenceValidatorWhenAutoCreateEnabledTest
    extends AnyFunSuite
    with ForEachTestContainer
    with Matchers {

  override val container: KafkaContainer =
    createKafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "TRUE"))

  private def kafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> container.bootstrapServers)),
    kafkaEspProperties = None,
    consumerGroupNamingStrategy = None,
    avroKryoGenericRecordSchemaIdSerialization = None,
    topicsExistenceValidationConfig = TopicsExistenceValidationConfigForTest.config
  )

  test("should validate not existing topic") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(TopicName.OfSource("not.existing")) shouldBe Symbol("valid")
  }

  test("should use cache when validating") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(TopicName.OfSource("not.existing")) shouldBe Symbol("valid")
    container.stop()
    v.validateTopic(TopicName.OfSource("not.existing")) shouldBe Symbol("valid")
  }

}
