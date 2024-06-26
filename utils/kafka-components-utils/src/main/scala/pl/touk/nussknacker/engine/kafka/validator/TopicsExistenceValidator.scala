package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toShow
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{Admin, ListTopicsOptions}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import scala.jdk.CollectionConverters._

trait TopicsExistenceValidator extends Serializable {

  final def validateTopic(topic: TopicName): Validated[TopicExistenceValidationException, TopicName] =
    validateTopics(List(topic)).map(_.head)

  def validateTopics(topics: List[TopicName]): Validated[TopicExistenceValidationException, List[TopicName]]
}

trait WithCachedTopicsExistenceValidator extends TopicsExistenceValidator {
  protected val kafkaConfig: KafkaConfig
  protected lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig)

  final override def validateTopics(
      topics: List[TopicName]
  ): Validated[TopicExistenceValidationException, List[TopicName]] =
    validator.validateTopics(topics)

}

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig) extends TopicsExistenceValidator with LazyLogging {
  private def config = kafkaConfig.topicsExistenceValidationConfig.validatorConfig

  @transient private lazy val topicsCache = new SingleValueCache[Set[String]](
    expireAfterAccess = None,
    expireAfterWrite = Some(config.topicsFetchCacheTtl)
  )

  def validateTopics(topics: List[TopicName]): Validated[TopicExistenceValidationException, List[TopicName]] = {
    if (kafkaConfig.topicsExistenceValidationConfig.enabled) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def doValidate(topics: List[TopicName]) = {
    topicsCache.get() match {
      case Some(cachedTopics) if doAllExists(topics, cachedTopics).isRight =>
        Valid(topics)
      case Some(_) | None =>
        fetchTopicsAndValidate(topics)
    }
  }

  private def doAllExists(requestedTopics: Iterable[TopicName], existingTopics: Set[String]) = {
    val notExistingTopics = requestedTopics.filterNot(topicName => existingTopics.contains(topicName.show))
    Either.cond(notExistingTopics.isEmpty, (), notExistingTopics.toList)
  }

  private def fetchTopicsAndValidate(requestedTopics: List[TopicName]) = {
    val existingTopics = fetchAllTopicsAndCache()
    doAllExists(requestedTopics, existingTopics) match {
      case Right(())               => Valid(requestedTopics)
      case Left(notExistingTopics) => Invalid(TopicExistenceValidationException(notExistingTopics))
    }
  }

  private def fetchAllTopicsAndCache() = {
    val existingTopics = usingAdminClient {
      _.listTopics(new ListTopicsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt))
        .names()
        .get()
        .asScala
        .toSet
    }
    topicsCache.put(existingTopics)
    existingTopics
  }

  private def usingAdminClient[T]: (Admin => T) => T = KafkaUtils.usingAdminClient[T](kafkaConfig)
}

final case class TopicExistenceValidationException(topics: List[TopicName])
    extends RuntimeException(
      s"Topic${if (topics.size > 1) "s" else ""} ${topics.map(_.show).mkString(", ")} ${if (topics.size > 1) "do"
        else "does"} not exist"
    ) {
  def toCustomNodeError(nodeId: String, paramName: Option[ParameterName]) =
    new CustomNodeError(nodeId, super.getMessage, paramName)
}
