package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.NewTopic
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionConsumerConfig
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import java.util
import java.util.Optional
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.asScalaSetConverter

class ErrorHandlerInitializer(kafkaConfig: KafkaConfig, exceptionHandlerConfig: KafkaExceptionConsumerConfig) extends LazyLogging {

  private val partitions = 2

  private val timeoutSeconds = 5

  def init(): Unit = {
    val errorTopic = exceptionHandlerConfig.topic
    KafkaUtils.usingAdminClient(kafkaConfig) { admin =>
      val topicNames = admin.listTopics().names().get(timeoutSeconds, TimeUnit.SECONDS)
      val topicExists = topicNames.asScala.contains(errorTopic)
      (topicExists, exceptionHandlerConfig.createTopicIfNotExists) match {
        case (true, _) =>
          logger.debug("Topic exists, skipping")
        case (false, true) =>
          logger.info(s"Creating error topic: $errorTopic with default config: partitions: $partitions, check if the values are correct")
          admin.createTopics(util.Arrays.asList(new NewTopic(errorTopic, Optional.of[java.lang.Integer](partitions), Optional.empty[java.lang.Short]())))
            .all().get(timeoutSeconds, TimeUnit.SECONDS)
        case (false, false) =>
          logger.error(s"Topic $errorTopic does not exist, createTopicIfNotExists == false")
          throw new IllegalArgumentException(s"Errors topic: $errorTopic does not exist, 'createTopicIfNotExists' is 'false', please create the topic")
      }
    }
  }

}
