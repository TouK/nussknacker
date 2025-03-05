package pl.touk.nussknacker.engine.kafka.validator

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{Admin, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
import org.apache.kafka.common.config.ConfigResource
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, UnspecializedTopicName}
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.kafka.validator.TopicsExistenceValidator.TopicValidationType
import pl.touk.nussknacker.engine.util.cache.SingleValueCache

import scala.jdk.CollectionConverters._

trait WithCachedTopicsExistenceValidator extends TopicsExistenceValidator {

  protected val kafkaConfig: KafkaConfig

  private lazy val validator = new CachedTopicsExistenceValidator(kafkaConfig)

  final override def validateTopics[T <: TopicName: TopicValidationType](
      topics: NonEmptyList[T]
  ): Validated[TopicExistenceValidationException[T], NonEmptyList[T]] =
    validator.validateTopics(topics)

}

class CachedTopicsExistenceValidator(kafkaConfig: KafkaConfig) extends TopicsExistenceValidator with LazyLogging {

  private val validatorConfig = kafkaConfig.topicsExistenceValidationConfig.validatorConfig

  @transient private lazy val autoCreateSettingCache = new SingleValueCache[Boolean](
    expireAfterAccess = None,
    expireAfterWrite = Some(validatorConfig.autoCreateFlagFetchCacheTtl)
  )

  @transient private lazy val topicsCache = new SingleValueCache[Set[UnspecializedTopicName]](
    expireAfterAccess = None,
    expireAfterWrite = Some(validatorConfig.topicsFetchCacheTtl)
  )

  def validateTopics[T <: TopicName: TopicValidationType](
      topics: NonEmptyList[T]
  ): Validated[TopicExistenceValidationException[T], NonEmptyList[T]] = {
    implicitly[TopicValidationType[T]] match {
      case TopicsExistenceValidator.SourceValidation => validateSourceTopics(topics)
      case TopicsExistenceValidator.SinkValidation   => validateSinkTopics(topics)
    }
  }

  private def validateSourceTopics[T <: TopicName: TopicValidationType](topics: NonEmptyList[T]) = {
    if (kafkaConfig.topicsExistenceValidationConfig.enabled) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def validateSinkTopics[T <: TopicName: TopicValidationType](topics: NonEmptyList[T]) = {
    if (kafkaConfig.topicsExistenceValidationConfig.enabled && !isAutoCreateEnabled) {
      doValidate(topics)
    } else {
      Valid(topics)
    }
  }

  private def doValidate[T <: TopicName](topics: NonEmptyList[T]) = {
    topicsCache.get() match {
      case Some(cachedTopics) if doAllExist(topics, cachedTopics).isRight =>
        Valid(topics)
      case Some(_) | None =>
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
    val existingTopics = fetchAllTopicsAndCache()
    doAllExist(requestedTopics, existingTopics) match {
      case Right(())               => Valid(requestedTopics)
      case Left(notExistingTopics) => Invalid(TopicExistenceValidationException(notExistingTopics))
    }
  }

  private def fetchAllTopicsAndCache() = {
    val existingTopics = usingAdminClient {
      _.listTopics(new ListTopicsOptions().timeoutMs(validatorConfig.adminClientTimeout.toMillis.toInt))
        .names()
        .get()
        .asScala
        .toSet
        .map(UnspecializedTopicName.apply)
    }
    topicsCache.put(existingTopics)
    existingTopics
  }

  private def isAutoCreateEnabled: Boolean = autoCreateSettingCache.getOrCreate {
    val timeout = validatorConfig.adminClientTimeout.toMillis.toInt
    val randomKafkaNodeId = usingAdminClient {
      _.describeCluster(new DescribeClusterOptions().timeoutMs(timeout)).nodes().get().asScala.head.id().toString
    }
    usingAdminClient {
      _.describeConfigs(
        List(new ConfigResource(ConfigResource.Type.BROKER, randomKafkaNodeId)).asJava,
        new DescribeConfigsOptions().timeoutMs(validatorConfig.adminClientTimeout.toMillis.toInt)
      )
        .values()
        .values()
        .asScala
        .map(_.get())
        .head // we ask for config of one node, but `describeConfigs` api have `List` of nodes, so here we got single element list
        .get("auto.create.topics.enable")
        .value()
        .toBoolean
    }
  }

  private def usingAdminClient[T]: (Admin => T) => T = KafkaUtils.usingAdminClient[T](kafkaConfig)

}
