package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated.{Invalid, Valid}
import com.dimafeng.testcontainers.{ForAllTestContainer, KafkaContainer}
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.kafka._

import java.util.Collections
import scala.concurrent.duration.DurationInt

class CachedTopicsExistenceValidatorTest extends AnyFunSuite with ForAllTestContainer with Matchers {

  override val container: KafkaContainer =
    KafkaContainer(DockerImageName.parse(s"${KafkaContainer.defaultImage}:7.4.0"))
      .configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "TRUE"))

  test("should validate existing topic") {
    val topic     = createTopic("test.topic.1")
    val validator = new CachedTopicsExistenceValidator(kafkaConfig)
    validator.validateTopic(topic.name()) shouldBe Valid(topic.name())
  }

  test("should validate not existing topic") {
    val validator = new CachedTopicsExistenceValidator(kafkaConfig)
    validator.validateTopic("not.existing") shouldBe Invalid(TopicExistenceValidationException("not.existing" :: Nil))
  }

  test("should not validate not existing topic when validation disabled") {
    val validator = new CachedTopicsExistenceValidator(
      kafkaConfig.copy(
        kafkaProperties = Some(Map("bootstrap.servers" -> "broken address")),
        topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)
      )
    )
    validator.validateTopic("not.existing") shouldBe Valid("not.existing")
  }

  test("should fetch topics every time when not valid using cache") {
    val topicName = "test.topic.2"
    val validator = new CachedTopicsExistenceValidator(kafkaConfig)
    validator.validateTopic(topicName) shouldBe Invalid(TopicExistenceValidationException(topicName :: Nil))

    createTopic(topicName)

    validator.validateTopic(topicName) shouldBe Valid("test.topic.2")
  }

  test("should use cache when validating") {
    val topic     = createTopic("test.topic.3")
    val validator = new CachedTopicsExistenceValidator(kafkaConfig)
    validator.validateTopic(topic.name()) shouldBe Valid(topic.name())
    container.stop()
    validator.validateTopic(topic.name()) shouldBe Valid(topic.name())
  }

  private def createTopic(name: String) = {
    val topic = new NewTopic(name, Collections.emptyMap())
    KafkaUtils.usingAdminClient(kafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](topic))
    }
    topic
  }

  private def kafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> container.bootstrapServers)),
    kafkaEspProperties = None,
    consumerGroupNamingStrategy = None,
    avroKryoGenericRecordSchemaIdSerialization = None,
    topicsExistenceValidationConfig = TopicsExistenceValidationConfig(
      enabled = true,
      validatorConfig = CachedTopicsExistenceValidatorConfig.DefaultConfig.copy(adminClientTimeout = 5 seconds)
    )
  )

}
