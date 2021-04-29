package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.apache.kafka.clients.admin.{Admin, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
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
  protected lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig)

  final override def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = validator.validateTopics(topics)
}

case class CachedTopicsExistenceValidatorConfig(autoCreateFlagFetchCacheTtl: FiniteDuration,
                                                topicsFetchCacheTtl: FiniteDuration,
                                                adminClientTimeout: FiniteDuration)

object CachedTopicsExistenceValidatorConfig {
  val AutoCreateTopicPropertyName = "auto.create.topics.enable"
  val TopicExistenceValidationEnabledPropertyName = "topic.existence.validation.enabled"
  val ValidationEnabledDefault = false
  val DefaultConfig = CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl = 5 minutes, topicsFetchCacheTtl = 30 seconds, adminClientTimeout = 500 millis
  )
}

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig) extends TopicsExistenceValidator with LazyLogging {
  private def config = kafkaConfig.topicsExistenceValidationConfig.getValidatorConfig
  private lazy val autoCreateSettingCache = new SingleValueCache[Boolean](expireAfterAccess = None, expireAfterWrite = Some(config.autoCreateFlagFetchCacheTtl))
  private lazy val topicListCache = new SingleValueCache[List[String]](expireAfterAccess = None, expireAfterWrite = Some(config.topicsFetchCacheTtl))

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = {
    if (!kafkaConfig.topicsExistenceValidationConfig.enabled || isAutoCreateEnabled) {
      Valid(topics)
    } else {
      val existingTopics = topicListCache.getOrCreate {
        usingAdminClient {
          _.listTopics(new ListTopicsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
            .names().get().asScala.toList
        }
      }
      val notExistingTopics = topics.diff(existingTopics)

      if (notExistingTopics.isEmpty)
        Valid(topics)
      else
        Invalid(new TopicExistenceValidationException(notExistingTopics))
    }
  }

  private def isAutoCreateEnabled: Boolean = autoCreateSettingCache.getOrCreate {
    val timeout = config.adminClientTimeout.toMillis.toInt
    val randomKafkaNodeId = usingAdminClient {
      _.describeCluster(new DescribeClusterOptions().timeoutMs(timeout)).nodes().get().asScala.head.id().toString
    }
    usingAdminClient {
      _
        .describeConfigs(List(new ConfigResource(ConfigResource.Type.BROKER, randomKafkaNodeId)).asJava,
          new DescribeConfigsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
        .values()
        .values()
        .asScala
        .map(_.get())
        .head // we ask for config of one node, but `describeConfigs` api have `List` of nodes, so here we got single element list
        .get(AutoCreateTopicPropertyName)
        .value()
        .toBoolean
    }
  }

  private def usingAdminClient[T]: (Admin => T) => T = KafkaUtils.usingAdminClient[T](kafkaConfig)
}

class TopicExistenceValidationException(topics: List[String])
  extends RuntimeException(s"Topic${if (topics.size > 1) "s" else ""} ${topics.mkString(", ")} ${if (topics.size > 1) "do" else "does"} not exist") {
  def toCustomNodeError(nodeId: String, paramName: Option[String]) = new CustomNodeError(nodeId, super.getMessage, paramName)
}