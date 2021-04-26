package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import java.util.Properties
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.apache.kafka.clients.admin.{Admin, AdminClient, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
import org.apache.kafka.common.config.ConfigResource
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.kafka.validator.CachedTopicsExistenceValidatorConfig._

import scala.collection.JavaConverters._

trait TopicsExistenceValidator {

  final def validateTopic(topic: String): Validated[TopicExistenceValidationException, String] = validateTopics(List(topic)).map(_.head)

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]]
}

trait WithCachedTopicsExistenceValidator extends TopicsExistenceValidator {
  protected val kafkaConfig: KafkaConfig
  protected val validatorConfig: CachedTopicsExistenceValidatorConfig = DefaultConfig
  private lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig, validatorConfig)

  final override def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = validator.validateTopics(topics)
}

case class CachedTopicsExistenceValidatorConfig(autoCreateFlagFetchCacheTtl: FiniteDuration, topicsFetchCacheTtl: FiniteDuration, adminClientTimeout: FiniteDuration)

object CachedTopicsExistenceValidatorConfig {
  val AutoCreateTopicPropertyName = "auto.create.topics.enable"
  val TopicExistenceValidationEnabledPropertyName = "topic.existence.validation.enabled"
  val ValidationEnabledDefault = false
  val DefaultConfig = CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl = 5 minutes, topicsFetchCacheTtl = 30 seconds, adminClientTimeout = 500 millis
  )
}

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig, config: CachedTopicsExistenceValidatorConfig)
  extends TopicsExistenceValidator with LazyLogging {

  private lazy val autoCreateSettingCache = new SingleValueCache[Boolean](expireAfterAccess = None, expireAfterWrite = Some(config.autoCreateFlagFetchCacheTtl))
  private lazy val topicListCache = new SingleValueCache[Set[String]](expireAfterAccess = None, expireAfterWrite = Some(config.topicsFetchCacheTtl))

  private lazy val adminClientProperties = {
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", kafkaConfig.kafkaAddress)
    properties
  }

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = {
    if (validationDisabled || isAutoCreateEnabled) {
      Valid(topics)
    } else {
      val existingTopics = topicListCache.getOrCreate {
        usingAdminClient {
          _.listTopics(new ListTopicsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
            .names().get().asScala.toSet
        }
      }
      val notExistingTopics = topics.diff(existingTopics.toList)

      if (notExistingTopics.isEmpty)
        Valid(topics)
      else
        Invalid(new TopicExistenceValidationException(notExistingTopics))
    }
  }

  def validationDisabled: Boolean = !kafkaConfig.kafkaEspProperties.flatMap(_.get(TopicExistenceValidationEnabledPropertyName))
    .map(_.toBoolean).getOrElse(ValidationEnabledDefault)

  private def isAutoCreateEnabled: Boolean = autoCreateSettingCache.getOrCreate {
    val timeout = config.adminClientTimeout.toMillis.toInt
    val id = usingAdminClient {
      _.describeCluster(new DescribeClusterOptions().timeoutMs(timeout)).nodes().get().asScala.head.id().toString
    }
    usingAdminClient {
      _
        .describeConfigs(List(new ConfigResource(ConfigResource.Type.BROKER, id)).asJava,
          new DescribeConfigsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
        .values()
        .values()
        .asScala
        .map(_.get())
        .head.get(AutoCreateTopicPropertyName)
        .value()
        .toBoolean
    }
  }

  private def usingAdminClient[T](adminClientOperation: Admin => T): T = {
    val c = AdminClient.create(adminClientProperties)
    try adminClientOperation(c)
    finally c.close()
  }
}

class TopicExistenceValidationException(topics: List[String])
  extends RuntimeException(s"Topic${if (topics.size > 1) "s" else ""} ${topics.mkString(", ")} ${if (topics.size > 1) "do" else "does"} not exist") {
  def toCustomNodeError(nodeId: String, paramName: Option[String]) = new CustomNodeError(nodeId, super.getMessage, paramName)
}