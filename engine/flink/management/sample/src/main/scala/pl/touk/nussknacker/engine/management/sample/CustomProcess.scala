package pl.touk.nussknacker.engine.management.sample

import java.util.Properties

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011

object CustomProcess {

  def main(args: Array[String]) : Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    //TODO
    //parallelism here could be greater than 1, but there is some docker config issue that comes up in travis build
    //it looks that `taskmanager.numberOfTaskSlots` property is set to 1 in flink task/job manager container, which is obviously too small
    env.setParallelism(1)

    val config = ConfigFactory.parseString(args(1))

    val props = new Properties()
    props.setProperty("zookeeper.connect", config.getString("kafka.zkAddress"))
    props.setProperty("bootstrap.servers", config.getString("kafka.kafkaAddress"))

    env.addSource(new FlinkKafkaConsumer011[String]("testTopic", new SimpleStringSchema, props))
      .printToErr()

    env.execute(args(0))
  }
}
