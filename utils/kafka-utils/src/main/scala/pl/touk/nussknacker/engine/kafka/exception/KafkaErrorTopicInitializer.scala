package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.NewTopic
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import java.util.Optional
import java.util.concurrent.TimeUnit
import java.{lang, util}
import scala.jdk.CollectionConverters.asScalaSetConverter

class KafkaErrorTopicInitializer(kafkaConfig: KafkaConfig, exceptionHandlerConfig: KafkaExceptionConsumerConfig) extends LazyLogging {

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
          logger.info(s"Creating error topic: $errorTopic with default configs, please check if the values are correct")
          val errorTopicConfig = new NewTopic(errorTopic, Optional.empty[Integer](), Optional.empty[lang.Short]())
          admin.createTopics(util.Arrays.asList(errorTopicConfig)).all().get(timeoutSeconds, TimeUnit.SECONDS)
        case (false, false) =>
          logger.error(s"Topic $errorTopic does not exist, createTopicIfNotExists == false")
          throw new IllegalArgumentException(s"Errors topic: $errorTopic does not exist, 'createTopicIfNotExists' is 'false', please create the topic")
      }
    }
  }

}
