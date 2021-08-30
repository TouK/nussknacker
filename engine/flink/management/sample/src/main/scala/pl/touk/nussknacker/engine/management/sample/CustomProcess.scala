package pl.touk.nussknacker.engine.management.sample

import com.github.ghik.silencer.silent

import java.util.Properties
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import pl.touk.nussknacker.engine.flink.util.FlinkArgsDecodeHack

import scala.annotation.nowarn

object CustomProcess {

  @silent("deprecated")
  @nowarn("cat=deprecation")
  def main(argsWithHack: Array[String]) : Unit = {
    val args =  FlinkArgsDecodeHack.prepareProgramArgs(argsWithHack)

    val env = StreamExecutionEnvironment.getExecutionEnvironment

    //TODO
    //parallelism here could be greater than 1, but there is some docker config issue that comes up in travis build
    //it looks that `taskmanager.numberOfTaskSlots` property is set to 1 in flink task/job manager container, which is obviously too small
    env.setParallelism(1)

    val config = ConfigFactory.parseString(args(1))

    val props = new Properties()
    props.setProperty("bootstrap.servers", config.getString("kafka.kafkaAddress"))

    env.addSource(new FlinkKafkaConsumer[String]("testTopic", new SimpleStringSchema, props))
      .printToErr()

    env.execute(args(0))
  }
}
