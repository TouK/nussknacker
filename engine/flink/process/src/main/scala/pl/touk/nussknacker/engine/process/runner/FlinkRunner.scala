package pl.touk.nussknacker.engine.process.runner
import java.io.File
import java.nio.charset.StandardCharsets

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

trait FlinkRunner {

  protected def parseProcessVersion(json: String): ProcessVersion =
    CirceUtil.decodeJson[ProcessVersion](json) match {
      case Right(p) => p
      case Left(err) => throw new IllegalArgumentException(s"ProcessVersion parse error $err")
    }

  protected def readConfigFromArgs(args: Array[String]): Config = {
    val optionalConfigArg = if (args.length > 2) Some(args(2)) else None
    readConfigFromArg(optionalConfigArg)
  }

  protected def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      val source = scala.io.Source.fromFile(arg.substring(1), StandardCharsets.UTF_8.name())
      try source.mkString finally source.close()
    } else {
      arg
    }
    ProcessMarshaller.fromJson(canonicalJson).toValidatedNel[Any, CanonicalProcess] andThen { canonical =>
      ProcessCanonizer.uncanonize(canonical.withoutDisabledNodes)
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
