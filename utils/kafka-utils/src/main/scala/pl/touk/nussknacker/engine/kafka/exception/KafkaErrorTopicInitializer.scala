package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{DescribeTopicsOptions, ListTopicsOptions, NewTopic}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import java.{lang, util}
import java.util.{Collections, Optional}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

trait KafkaErrorTopicInitializer {
  def topicName: String
  def init(): Unit
}

class DefaultKafkaErrorTopicInitializer(kafkaConfig: KafkaConfig, exceptionHandlerConfig: KafkaExceptionConsumerConfig)
    extends KafkaErrorTopicInitializer
    with LazyLogging {

  private val timeoutSeconds = 5

  val topicName: String = exceptionHandlerConfig.topic

  def init(): Unit = {
    KafkaUtils.usingAdminClient(kafkaConfig) { admin =>
      val topicNames = admin
        .listTopics(new ListTopicsOptions().timeoutMs(timeoutSeconds * 1000))
        .names()
        .get(timeoutSeconds, TimeUnit.SECONDS)
      val topicExists = topicNames.asScala.contains(topicName)
      if (topicExists) {
        logger.debug(s"Topic $topicName already exists, skipping")
      } else {
        logger.info(s"Creating error topic: $topicName with default configs, please check if the values are correct")
        val errorTopicConfig = new NewTopic(topicName, Optional.empty[Integer](), Optional.empty[lang.Short]())
        try {
          admin.createTopics(util.Arrays.asList(errorTopicConfig)).all().get(timeoutSeconds, TimeUnit.SECONDS)
        } catch {
          case NonFatal(e) =>
            throw new IllegalStateException(
              s"Failed to create $topicName (${e.getMessage}), cannot run scenario properly",
              e
            )
        }
      }
    }
  }

}

object NoopKafkaErrorTopicInitializer extends KafkaErrorTopicInitializer {

  override val topicName: String = "-"

  override def init(): Unit = {}
}
