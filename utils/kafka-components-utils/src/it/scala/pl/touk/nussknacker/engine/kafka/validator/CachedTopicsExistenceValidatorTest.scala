package pl.touk.nussknacker.engine.kafka.validator

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.dimafeng.testcontainers.{ForAllTestContainer, KafkaContainer}
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka._

import java.util.Collections
import scala.concurrent.duration.DurationInt

class CachedTopicsExistenceValidatorWhenAutoCreateDisabledTest
    extends BaseCachedTopicsExistenceValidatorTest(
      kafkaAutoCreateEnabled = false
    ) {

  test("should validate existing topic") {
    val topic     = createSourceTopic("test.topic.1")
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(topic) shouldBe Valid(topic)
  }

  test("should validate not existing topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSourceTopic) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingSourceTopic))
    )
  }

  test("should not validate not existing topic when validation disabled") {
    val validator = new CachedTopicsExistenceValidator(
      defaultKafkaConfig.copy(
        kafkaProperties = Some(Map("bootstrap.servers" -> "broken address")),
        topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)
      )
    )
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
  }

  test("should fetch topics every time when not valid using cache") {
    val topicName           = "test.topic.2"
    val notExistingYetTopic = TopicName.ForSource(topicName)
    val validator           = new CachedTopicsExistenceValidator(defaultKafkaConfig)

    validator.validateTopic(notExistingYetTopic) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingYetTopic))
    )

    val topic = createSourceTopic(topicName)

    validator.validateTopic(topic) shouldBe Valid(topic)
  }

}

class CachedTopicsExistenceValidatorWhenAutoCreateEnabledTest
    extends BaseCachedTopicsExistenceValidatorTest(
      kafkaAutoCreateEnabled = true
    ) {

  test("should validate not existing topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
  }

  test("should use cache when validating") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
    container.stop()
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
  }

}

abstract class BaseCachedTopicsExistenceValidatorTest(kafkaAutoCreateEnabled: Boolean)
    extends AnyFunSuite
    with ForAllTestContainer
    with Matchers {

  override val container: KafkaContainer =
    KafkaContainer(DockerImageName.parse(s"${KafkaContainer.defaultImage}:7.4.0"))
      .configure {
        _.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", kafkaAutoCreateEnabled.toString.toUpperCase)
      }

  lazy val defaultKafkaConfig: KafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> container.bootstrapServers)),
    kafkaEspProperties = None,
    consumerGroupNamingStrategy = None,
    avroKryoGenericRecordSchemaIdSerialization = None,
    // longer timeout, as container might need some time to make initial assignments etc.
    topicsExistenceValidationConfig = TopicsExistenceValidationConfig(
      enabled = true,
      validatorConfig = CachedTopicsExistenceValidatorConfig.DefaultConfig.copy(adminClientTimeout = 30 seconds)
    )
  )

  val notExistingSourceTopic: TopicName.ForSource = TopicName.ForSource("not.existing")

  protected def createSourceTopic(name: String): TopicName.ForSource = {
    createKafkaTopic(name)
    TopicName.ForSource(name)
  }

  protected def createSinkTopic(name: String): TopicName.ForSink = {
    createKafkaTopic(name)
    TopicName.ForSink(name)
  }

  private def createKafkaTopic(name: String): Unit = {
    val topic = new NewTopic(name, Collections.emptyMap())
    KafkaUtils.usingAdminClient(defaultKafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](topic))
    }
  }

}
