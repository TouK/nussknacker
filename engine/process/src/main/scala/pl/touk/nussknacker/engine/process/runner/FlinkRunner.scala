package pl.touk.nussknacker.engine.process.runner

import java.io.{File, FileReader}
import java.nio.charset.StandardCharsets

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.ThreadUtils

trait FlinkRunner {

  private val ProcessMarshaller = new ProcessMarshaller


  protected def readConfigFromArgs(args: Array[String]): Config = {
    val optionalConfigArg = if (args.length > 1) Some(args(1)) else None
    readConfigFromArg(optionalConfigArg)
  }

  protected def loadCreator(config: Config): ProcessConfigCreator =
    ThreadUtils.loadUsingContextLoader(config.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]

  protected def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      val source = scala.io.Source.fromFile(arg.substring(1), StandardCharsets.UTF_8.name())
      try source.mkString finally source.close()
    } else {
      arg
    }
    ProcessMarshaller.fromJson(canonicalJson).toValidatedNel[Any, CanonicalProcess] andThen { canonical =>
      ProcessCanonizer.uncanonize(canonical)
    } match {
      case Valid(p) => p
      case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
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
