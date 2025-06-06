package pl.touk.nussknacker.engine.kafka.validator

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{CachingKafkaAdminClient, KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.kafka.validator.TopicsExistenceValidator.TopicValidationType

object CachedTopicsExistenceValidator {

  def apply(kafkaConfig: KafkaConfig): CachedTopicsExistenceValidator = {
    new CachedTopicsExistenceValidator(
      kafkaConfig.topicsExistenceValidationConfig.enabled,
      KafkaUtils.createCachingAdminClient(kafkaConfig)
    )
  }

}

class CachedTopicsExistenceValidator(enabled: Boolean, cachingKafkaAdminClient: CachingKafkaAdminClient)
    extends TopicsExistenceValidator
    with LazyLogging {

  def validateTopics[T <: TopicName: TopicValidationType](
      topics: NonEmptyList[T]
  ): Validated[TopicExistenceValidationException[T], NonEmptyList[T]] = {
    implicitly[TopicValidationType[T]] match {
      case TopicsExistenceValidator.SourceValidation => validateSourceTopics(topics)
      case TopicsExistenceValidator.SinkValidation   => validateSinkTopics(topics)
    }
  }

  private def validateSourceTopics[T <: TopicName: TopicValidationType](topics: NonEmptyList[T]) = {
    if (enabled) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def validateSinkTopics[T <: TopicName: TopicValidationType](topics: NonEmptyList[T]) = {
    if (enabled && !cachingKafkaAdminClient.getOrFetchAutoCreateTopicsSetting) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def doValidate[T <: TopicName](topics: NonEmptyList[T]) = {
    cachingKafkaAdminClient.getCachedTopics match {
      case Some(cachedTopics) if doAllExist(topics, cachedTopics).isRight =>
        Valid(topics)
      case Some(_) =>
        logger.debug("Topics do not exist, fetch topics from Kafka and validate")
        fetchTopicsAndValidate(topics)
      case None =>
        logger.debug("Empty topics cache, fetch topics from Kafka and validate")
        fetchTopicsAndValidate(topics)
    }
  }

  private def doAllExist[T <: TopicName](
      requestedTopics: NonEmptyList[T],
      existingTopics: Set[UnspecializedTopicName]
  ) = {
    val notExistingTopics = requestedTopics.filterNot(topicName => existingTopics.contains(topicName.toUnspecialized))
    NonEmptyList.fromList(notExistingTopics) match {
      case None      => Right(())
      case Some(nel) => Left(nel)
    }
  }

  private def fetchTopicsAndValidate[T <: TopicName](requestedTopics: NonEmptyList[T]) = {
    val existingTopics = cachingKafkaAdminClient.fetchFreshTopicsAndCache
    doAllExist(requestedTopics, existingTopics) match {
      case Right(())               => Valid(requestedTopics)
      case Left(notExistingTopics) => Invalid(TopicExistenceValidationException(notExistingTopics))
    }
  }

}
