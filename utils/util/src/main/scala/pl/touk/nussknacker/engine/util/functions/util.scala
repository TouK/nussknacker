package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.util.MathUtils

import java.util.UUID

object util {

  @Documentation(description = "Generate unique identifier (https://en.wikipedia.org/wiki/Universally_unique_identifier)")
  def uuid: String = UUID.randomUUID().toString

}
