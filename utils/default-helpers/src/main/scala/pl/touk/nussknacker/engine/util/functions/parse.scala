package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

class parse extends ParseUtils

trait ParseUtils {

  @Documentation(description = "???")
  def toUnknown(@ParamName("value") value: String): Any = {
    ???
  }

}
