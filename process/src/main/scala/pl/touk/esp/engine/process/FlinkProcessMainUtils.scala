package pl.touk.esp.engine.process

import java.io.FileReader

import cats.data.Validated.{Invalid, Valid}
import cats.std.list._
import org.apache.commons.io.IOUtils
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller

object FlinkProcessMainUtils {

  def readProcessFromArgs(args: Array[String]) = {
    require(args.nonEmpty, "Process josn should be passed as a first argument")
    readProcessFromArg(args(0))
  }

  def readProcessFromArg(arg: String): EspProcess = {
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
