package pl.touk.esp.engine.process

import java.io.{File, FileReader}

import cats.data.Validated.{Invalid, Valid}
import cats.std.list._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller

object FlinkProcessMain {

  def main(args: Array[String]) : Unit = {

    require(args.nonEmpty, "Process json should be passed as a first argument")
    val process = readProcessFromArg(args(0))
    val optionalConfigArg = if (args.length > 1) Some(args(1)) else None
    val config = readConfigFromArg(optionalConfigArg)
    val registrar: FlinkProcessRegistrar = prepareRegistrar(config)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    registrar.register(env, process)
    env.execute(process.id)
  }

  private def prepareRegistrar(config: Config): FlinkProcessRegistrar = {
    val creator = Thread.currentThread.getContextClassLoader.loadClass(config.getString("processConfigCreatorClass"))
      .newInstance().asInstanceOf[ProcessConfigCreator]

    FlinkProcessRegistrar(creator, config)
  }

  private def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      IOUtils.toString(new FileReader(arg.substring(1)))
    } else {
      arg
    }
    ProcessMarshaller.fromJson(canonicalJson) match {
      case Valid(p) => p
      case Invalid(err) => throw new IllegalArgumentException(err.unwrap.mkString("Compilation errors: ", ", ", ""))
    }
  }

  private def readConfigFromArg(arg: Option[String]): Config =
    arg match {
      case Some(name) if name.startsWith("@") =>
        ConfigFactory.parseFile(new File(name.substring(1)))
      case Some(string) =>
        ConfigFactory.parseString(string)
      case None =>
        ConfigFactory.load()
    }


}
