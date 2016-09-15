package pl.touk.esp.engine.management.sample

import java.util.Properties

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer09
import org.apache.flink.streaming.util.serialization.SimpleStringSchema

object CustomProcess {

  def main(args: Array[String]) : Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    env.setParallelism(3)

    val config = ConfigFactory.parseString(args(1))

    val props = new Properties()
    props.setProperty("zookeeper.connect", config.getString("kafka.zkAddress"))
    props.setProperty("bootstrap.servers", config.getString("kafka.kafkaAddress"))

    env.addSource(new FlinkKafkaConsumer09[String]("testTopic", new SimpleStringSchema, props))
      .printToErr()

    env.execute(args(0))
  }
}
