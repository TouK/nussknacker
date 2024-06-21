package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{Admin, ListTopicsOptions}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import scala.jdk.CollectionConverters._

trait TopicsExistenceValidator extends Serializable {

  final def validateTopic(topic: String): Validated[TopicExistenceValidationException, String] =
    validateTopics(List(topic)).map(_.head)

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]]
}

trait WithCachedTopicsExistenceValidator extends TopicsExistenceValidator {
  protected val kafkaConfig: KafkaConfig
  protected lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig)

  final override def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] =
    validator.validateTopics(topics)
}

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig) extends TopicsExistenceValidator with LazyLogging {
  private def config = kafkaConfig.topicsExistenceValidationConfig.validatorConfig

  @transient private lazy val topicListCache =
    new SingleValueCache[List[String]](expireAfterAccess = None, expireAfterWrite = Some(config.topicsFetchCacheTtl))

  def validateTopics(topics: List[String]): Validated[TopicExistenceValidationException, List[String]] = {
    if (kafkaConfig.topicsExistenceValidationConfig.enabled) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def doValidate(topics: List[String]) = {
    topicListCache.get() match {
      case Some(cachedTopics) if topics.diff(cachedTopics).isEmpty =>
        Valid(topics)
      case Some(_) | None =>
        fetchTopicsAndValidate(topics)
    }
  }

  private def fetchTopicsAndValidate(requestedTopics: List[String]) = {
    val existingTopics    = fetchAllTopicsAndCache()
    val notExistingTopics = requestedTopics.diff(existingTopics)
    if (notExistingTopics.isEmpty) Valid(requestedTopics)
    else Invalid(TopicExistenceValidationException(notExistingTopics))
  }

  private def fetchAllTopicsAndCache() = {
    val existingTopics = usingAdminClient {
      _.listTopics(new ListTopicsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
        .names()
        .get()
        .asScala
        .toList
    }
    topicListCache.put(existingTopics)
    existingTopics
  }

  private def usingAdminClient[T]: (Admin => T) => T = KafkaUtils.usingAdminClient[T](kafkaConfig)
}

final case class TopicExistenceValidationException(topics: List[String])
    extends RuntimeException(
      s"Topic${if (topics.size > 1) "s" else ""} ${topics.mkString(", ")} ${if (topics.size > 1) "do" else "does"} not exist"
    ) {
  def toCustomNodeError(nodeId: String, paramName: Option[ParameterName]) =
    new CustomNodeError(nodeId, super.getMessage, paramName)
}
