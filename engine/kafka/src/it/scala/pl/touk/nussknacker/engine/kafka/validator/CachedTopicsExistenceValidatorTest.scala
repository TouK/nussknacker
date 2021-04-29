package pl.touk.nussknacker.engine.kafka.validator

import com.dimafeng.testcontainers.{ForAllTestContainer, KafkaContainer}
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, TopicsExistenceValidationConfig}

import java.util.Collections

class CachedTopicsExistenceValidatorWhenAutoCreateDisabledTest extends FunSuite with ForAllTestContainer with Matchers {
  override val container: KafkaContainer = KafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "FALSE"))

  private def kafkaConfig = KafkaConfig(container.bootstrapServers, None, None, None, None, TopicsExistenceValidationConfig(enabled = true))

  test("should validate existing topic") {
    val topic = new NewTopic("test.topic.1", Collections.emptyMap())
    KafkaUtils.usingAdminClient(kafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](topic))
    }
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic(topic.name()) shouldBe 'valid
  }

  test("should validate not existing topic") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic("not.existing") shouldBe 'invalid
  }

  test("should not validate not existing topic when validation disabled") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig.copy(
      kafkaAddress = "broken address",
      topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)))
    v.validateTopic("not.existing") shouldBe 'valid
  }
}

class CachedTopicsExistenceValidatorWhenAutoCreateEnabledTest extends FunSuite with ForAllTestContainer with Matchers {
  override val container: KafkaContainer = KafkaContainer().configure(_.withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "TRUE"))
  private def kafkaConfig = KafkaConfig(container.bootstrapServers, None, None, None, None, TopicsExistenceValidationConfig(enabled = true))

  test("should validate not existing topic") {
    val v = new CachedTopicsExistenceValidator(kafkaConfig)
    v.validateTopic("not.existing") shouldBe 'valid
  }
}

