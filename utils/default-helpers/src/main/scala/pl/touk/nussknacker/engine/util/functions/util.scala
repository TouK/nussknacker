package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

import java.util.{Base64, UUID}
import scala.jdk.CollectionConverters._

object util extends UtilFunctions

trait UtilFunctions extends HideToString {

  @Documentation(description =
    "Generate unique identifier (https://en.wikipedia.org/wiki/Universally_unique_identifier)"
  )
  def uuid: String = UUID.randomUUID().toString

  // TODO: can be removed once we have a better support for Java arrays in SpEL expressions, then we could simply use `"".split("regex")`
  @Documentation(description =
    "Splits given text into a list of Strings using given regular expression. Note that trailing empty strings won't be discarded; e.g. split('a|b|', '|') returns a list of three elements, the last of which is an empty string"
  )
  def split(@ParamName("text") text: String, @ParamName("regexp") regexp: String): java.util.List[String] = {
    text.split(regexp, -1).toList.asJava
  }

  @Documentation(description = "???") // todo:
  def toJson(@ParamName("text") text: String, @ParamName("regexp") regexp: String): java.util.List[String] = {
    text.split(regexp, -1).toList.asJava
  }

  def parseToJson
}
