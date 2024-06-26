package pl.touk.nussknacker.engine.kafka.validator

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits.toShow
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{Admin, DescribeClusterOptions, DescribeConfigsOptions, ListTopicsOptions}
import org.apache.kafka.common.config.ConfigResource
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.UncategorizedTopicName.ToUncategorizedTopicName
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, UncategorizedTopicName}
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

  @transient private lazy val autoCreateSettingCache = new SingleValueCache[Boolean](
    expireAfterAccess = None,
    expireAfterWrite = Some(config.autoCreateFlagFetchCacheTtl)
  )

  @transient private lazy val topicsCache = new SingleValueCache[Set[UncategorizedTopicName]](
    expireAfterAccess = None,
    expireAfterWrite = Some(config.topicsFetchCacheTtl)
  )

  def validateTopics(topics: List[TopicName]): Validated[TopicExistenceValidationException, List[TopicName]] = {
    if (!kafkaConfig.topicsExistenceValidationConfig.enabled || isAutoCreateEnabled()) {
      Valid(topics)
    } else {
      doValidate(topics)
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

  private def doAllExists(requestedTopics: Iterable[TopicName], existingTopics: Set[UncategorizedTopicName]) = {
    val notExistingTopics =
      requestedTopics.filterNot(topicName => existingTopics.contains(topicName.toUncategorizedTopicName))
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
        .map(UncategorizedTopicName.apply)
    }
    topicsCache.put(existingTopics)
    existingTopics
  }

  private def usingAdminClient[T]: (Admin => T) => T = KafkaUtils.usingAdminClient[T](kafkaConfig)

  private def isAutoCreateEnabled(): Boolean = autoCreateSettingCache.getOrCreate {
    val timeout = config.adminClientTimeout.toMillis.toInt
    val randomKafkaNodeId = usingAdminClient {
      _.describeCluster(new DescribeClusterOptions().timeoutMs(timeout)).nodes().get().asScala.head.id().toString
    }
    usingAdminClient {
      _.describeConfigs(
        List(new ConfigResource(ConfigResource.Type.BROKER, randomKafkaNodeId)).asJava,
        new DescribeConfigsOptions().timeoutMs(config.adminClientTimeout.toMillis.toInt)
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

}

final case class TopicExistenceValidationException(topics: List[TopicName])
    extends RuntimeException(
      s"Topic${if (topics.size > 1) "s" else ""} ${topics.map(_.show).mkString(", ")} ${if (topics.size > 1) "do"
        else "does"} not exist"
    ) {
  def toCustomNodeError(nodeId: String, paramName: Option[ParameterName]) =
    new CustomNodeError(nodeId, super.getMessage, paramName)
}
