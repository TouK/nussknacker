package pl.touk.esp.engine.process

import java.io.FileReader
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import cats.std.list._

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.IOUtils
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.esp.engine.InterpreterConfig
import pl.touk.esp.engine.api.{SkipExceptionHandler, EspExceptionHandler, EspExceptionInfo}
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

object FlinkProcessMain {

  def main(args: Array[String]) : Unit = {

    val process = readProcessFromArgs(args)
    val registrar: FlinkProcessRegistrar = prepareRegistrar(args)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    registrar.register(env, process)
    env.execute(process.id)
  }

  private def prepareRegistrar(args: Array[String]): FlinkProcessRegistrar = {

    val config = ConfigFactory.load().getConfig( args(1))

    val timeout = config.getDuration("timeout", TimeUnit.SECONDS).seconds

    val creator = Thread.currentThread.getContextClassLoader.loadClass(config.getString("processConfigCreatorClass"))
      .newInstance().asInstanceOf[ProcessConfigCreator]

    new FlinkProcessRegistrar(
      () => new InterpreterConfig(creator.services(config), creator.listeners(config)),
      creator.sourceFactories(config),
      creator.sinkFactories(config),
      () => SkipExceptionHandler,
      timeout
    )
  }

  private def readProcessFromArgs(args: Array[String]) = {
    require(args.nonEmpty, "Process json should be passed as a first argument")
    readProcessFromArg(args(0))
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
}
