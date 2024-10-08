package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

import java.util.Base64

object base64 extends Base64Utils

trait Base64Utils {

  @Documentation(description = "Decode Base64 value to String")
  def decode(@ParamName("value") value: String): String = {
    new String(Base64.getDecoder.decode(value.getBytes("UTF-8")))
  }

  @Documentation(description = "Encode String value to Base64")
  def encode(@ParamName("value") value: String): String = {
    new String(Base64.getEncoder.encode(value.getBytes("UTF-8")))
  }

}
