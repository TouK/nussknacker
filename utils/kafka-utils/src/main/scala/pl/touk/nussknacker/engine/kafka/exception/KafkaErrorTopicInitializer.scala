package pl.touk.nussknacker.engine.kafka.exception

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.admin.{CreateTopicsOptions, DescribeTopicsOptions, NewTopic}
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, KafkaUtils}

import java.{lang, util}
import java.util.Optional
import scala.concurrent.ExecutionException
import scala.util.control.NonFatal

trait KafkaErrorTopicInitializer {
  def topicName: String
  def init(): Unit
}

class DefaultKafkaErrorTopicInitializer(
    kafkaComponentsConfig: KafkaComponentsConfig,
    exceptionHandlerConfig: KafkaExceptionConsumerConfig
) extends KafkaErrorTopicInitializer
    with LazyLogging {

  private val timeoutSeconds = 5

  @volatile private var initialized = false

  val topicName: String = exceptionHandlerConfig.topic

  def init(): Unit = {
    if (!initialized) {
      this.synchronized {
        if (!initialized) {
          createErrorTopic()
          initialized = true
        }
      }
    }
  }

  private def createErrorTopic(): Unit = {
    val timeoutMs = timeoutSeconds * 1000

    KafkaUtils.usingAdminClient(kafkaComponentsConfig) { admin =>
      val topicExists =
        try {
          admin
            .describeTopics(util.Arrays.asList(topicName), new DescribeTopicsOptions().timeoutMs(timeoutMs))
            .allTopicNames()
            .get()
          true
        } catch {
          case e: ExecutionException if e.getCause.isInstanceOf[UnknownTopicOrPartitionException] =>
            false
        }

      if (topicExists) {
        logger.debug(s"Topic $topicName already exists, skipping")
      } else {
        logger.info(s"Creating error topic: $topicName with default configs, please check if the values are correct")
        val errorTopicConfig = new NewTopic(topicName, Optional.empty[Integer](), Optional.empty[lang.Short]())
        try {
          admin
            .createTopics(util.Arrays.asList(errorTopicConfig), new CreateTopicsOptions().timeoutMs(timeoutMs))
            .all()
            .get()
          // note that at this point topic exists but its partitions may not have their leaders assigned
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
