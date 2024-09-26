package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{ListTopicsOptions, NewTopic}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import java.util.Optional
import java.util.concurrent.TimeUnit
import java.{lang, util}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class KafkaErrorTopicInitializer(kafkaConfig: KafkaConfig, val exceptionHandlerConfig: KafkaExceptionConsumerConfig)
    extends LazyLogging {

  private val timeoutSeconds = 5

  def init(): Unit = {
    val errorTopic = exceptionHandlerConfig.topic
    KafkaUtils.usingAdminClient(kafkaConfig) { admin =>
      val topicNames = admin
        .listTopics(new ListTopicsOptions().timeoutMs(timeoutSeconds * 1000))
        .names()
        .get(timeoutSeconds, TimeUnit.SECONDS)
      val topicExists = topicNames.asScala.contains(errorTopic)
      if (topicExists) {
        logger.debug("Topic exists, skipping")
      } else {
        logger.info(s"Creating error topic: $errorTopic with default configs, please check if the values are correct")
        val errorTopicConfig = new NewTopic(errorTopic, Optional.empty[Integer](), Optional.empty[lang.Short]())
        try {
          admin.createTopics(util.Arrays.asList(errorTopicConfig)).all().get(timeoutSeconds, TimeUnit.SECONDS)
        } catch {
          case NonFatal(e) =>
            throw new IllegalStateException(
              s"Failed to create $errorTopic (${e.getMessage}), cannot run scenario properly",
              e
            )
        }
      }
    }
  }

}
