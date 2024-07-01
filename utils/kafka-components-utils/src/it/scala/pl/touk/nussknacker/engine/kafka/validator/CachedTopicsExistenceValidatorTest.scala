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

import java.util.{Collections, UUID}
import scala.concurrent.duration.DurationInt

class CachedTopicsExistenceValidatorWhenAutoCreateDisabledTest
    extends BaseCachedTopicsExistenceValidatorTest(
      kafkaAutoCreateEnabled = false
    ) {

  test("should validate existing source topic") {
    val topic     = createUniqueSourceTopic()
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(topic) shouldBe Valid(topic)
  }

  test("should validate not existing source topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSourceTopic) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingSourceTopic))
    )
  }

  test("should validate existing sink topic") {
    val topic     = createUniqueSinkTopic()
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(topic) shouldBe Valid(topic)
  }

  test("should validate not existing sink topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSinkTopic) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingSinkTopic))
    )
  }

  test("should not validate not existing topic when validation disabled") {
    val validator = new CachedTopicsExistenceValidator(
      defaultKafkaConfig.copy(
        topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)
      )
    )
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
  }

  test("should fetch topics every time when not valid using cache") {
    val notExistingYetTopicName = createUniqueSourceTopicName()
    val validator               = new CachedTopicsExistenceValidator(defaultKafkaConfig)

    validator.validateTopic(notExistingYetTopicName) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingYetTopicName))
    )

    createSourceTopic(notExistingYetTopicName)

    validator.validateTopic(notExistingYetTopicName) shouldBe Valid(notExistingYetTopicName)
  }

}

class CachedTopicsExistenceValidatorWhenAutoCreateEnabledTest
    extends BaseCachedTopicsExistenceValidatorTest(
      kafkaAutoCreateEnabled = true
    ) {

  test("should validate existing source topic") {
    val topic     = createUniqueSourceTopic()
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(topic) shouldBe Valid(topic)
  }

  test("should not validate not existing source topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSourceTopic) shouldBe Invalid(
      TopicExistenceValidationException(NonEmptyList.one(notExistingSourceTopic))
    )
  }

  test("should validate existing sink topic") {
    val topic     = createUniqueSinkTopic()
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(topic) shouldBe Valid(topic)
  }

  test("should validate not existing sink topic") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSinkTopic) shouldBe Valid(notExistingSinkTopic)
  }

  test("should not validate not existing topic when validation disabled") {
    val validator = new CachedTopicsExistenceValidator(
      defaultKafkaConfig.copy(
        topicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false)
      )
    )
    validator.validateTopic(notExistingSourceTopic) shouldBe Valid(notExistingSourceTopic)
  }

  test("should use cache when validating") {
    val validator = new CachedTopicsExistenceValidator(defaultKafkaConfig)
    validator.validateTopic(notExistingSinkTopic) shouldBe Valid(notExistingSinkTopic)
    container.stop()
    validator.validateTopic(notExistingSinkTopic) shouldBe Valid(notExistingSinkTopic)
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

  val notExistingSourceTopic: TopicName.ForSource = TopicName.ForSource("source.not.existing")

  val notExistingSinkTopic: TopicName.ForSink = TopicName.ForSink("sink.not.existing")

  protected def createUniqueSourceTopic(): TopicName.ForSource = {
    val name = createUniqueSourceTopicName()
    createSourceTopic(name)
  }

  protected def createSourceTopic(sourceTopicName: TopicName.ForSource): TopicName.ForSource = {
    createKafkaTopic(sourceTopicName.name)
    sourceTopicName
  }

  protected def createUniqueSinkTopic(): TopicName.ForSink = {
    val sinkTopicName = createUniqueSinkTopicName()
    createKafkaTopic(sinkTopicName.name)
    sinkTopicName
  }

  protected def createUniqueSourceTopicName(): TopicName.ForSource =
    TopicName.ForSource(s"source-${UUID.randomUUID().toString.take(8)}")

  protected def createUniqueSinkTopicName(): TopicName.ForSink =
    TopicName.ForSink(s"sink-${UUID.randomUUID().toString.take(8)}")

  private def createKafkaTopic(name: String): Unit = {
    val topic = new NewTopic(name, Collections.emptyMap())
    KafkaUtils.usingAdminClient(defaultKafkaConfig) {
      _.createTopics(Collections.singletonList[NewTopic](topic))
    }
  }

}
