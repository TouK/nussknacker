package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import java.util.Properties
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.apache.kafka.clients.admin.{AdminClient, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
import org.apache.kafka.common.config.ConfigResource
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError

import scala.collection.JavaConverters._

trait TopicsExistenceValidator {

  final def validateTopic(topic: String): Validated[TopicExistenceValidationException, String] = validateTopics(List(topic)).map(_.head)

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]]
}

trait WithCachedTopicsExistenceValidator extends TopicsExistenceValidator {
  private lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig, config)
  protected val kafkaConfig: KafkaConfig
  protected val config: CachedTopicsExistenceValidatorConfig = CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl = 5 minutes, topicsFetchCacheTtl = 30 seconds, adminClientTimeout = 500 millis
  )

  final override def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = validator.validateTopics(topics)
}

case class CachedTopicsExistenceValidatorConfig(autoCreateFlagFetchCacheTtl: FiniteDuration, topicsFetchCacheTtl: FiniteDuration, adminClientTimeout: FiniteDuration)

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig, config: CachedTopicsExistenceValidatorConfig)
  extends TopicsExistenceValidator with LazyLogging {

  private lazy val autoCreateSettingCache = new SingleValueCache[Boolean](expireAfterAccess = None, expireAfterWrite = Some(config.autoCreateFlagFetchCacheTtl))
  private lazy val topicListCache = new SingleValueCache[Set[String]](expireAfterAccess = None, expireAfterWrite = Some(config.topicsFetchCacheTtl))
  private lazy val kafkaAdminClient = {
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", kafkaConfig.kafkaAddress)
    logger.info(kafkaConfig.kafkaAddress)
    AdminClient.create(properties)
  }

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] =
    isAutoCreateEnabled
      .andThen(if (_) Valid(topics) else {
        catchedErrorToInvalid {
          topicListCache.getOrCreate {
            kafkaAdminClient.listTopics(new ListTopicsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt)).names().get().asScala.toSet
          }
        }.andThen { existingTopics =>
          val notExistingTopics = topics.diff(existingTopics.toList)

          if (notExistingTopics.isEmpty)
            Valid(topics)
          else
            Invalid(TopicExistenceValidationException.notExistingTopics(notExistingTopics))
        }
      })


  private def isAutoCreateEnabled: Validated[TopicExistenceValidationException, Boolean] = catchedErrorToInvalid {
    autoCreateSettingCache.getOrCreate {
      val timeout = config.adminClientTimeout.toMillis.toInt
      val id = kafkaAdminClient
        .describeCluster(new DescribeClusterOptions().timeoutMs(timeout)).nodes().get().asScala.head.id().toString
      kafkaAdminClient
        .describeConfigs(List(new ConfigResource(ConfigResource.Type.BROKER, id)).asJava,
          new DescribeConfigsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
        .values()
        .values()
        .asScala
        .map(_.get())
        .headOption
        .map(_.get("auto.create.topics.enable").value())
        .map(_.toBoolean)
        .get
    }
  }

  private def catchedErrorToInvalid[T](action: => T): Validated[TopicExistenceValidationException, T] = try {
    Valid(action)
  } catch {
    case t: Throwable => {
      logger.error("Error while validating topic existence", t)
      Invalid(TopicExistenceValidationException.errorWhileValidating(t))
    }
  }

}

object TopicExistenceValidationException {
  def notExistingTopics(topics: List[String]) =
    new TopicExistenceValidationException(s"Topic${if (topics.size > 1) "s" else ""} ${topics.mkString(", ")} ${if (topics.size > 1) "do" else "does"} not exist", null)

  def errorWhileValidating(cause: Throwable) = new TopicExistenceValidationException("Error while validating topics existence", cause)
}

class TopicExistenceValidationException(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def toCustomNodeError(nodeId: String, paramName: Option[String]) = new CustomNodeError(nodeId, msg, paramName)
}