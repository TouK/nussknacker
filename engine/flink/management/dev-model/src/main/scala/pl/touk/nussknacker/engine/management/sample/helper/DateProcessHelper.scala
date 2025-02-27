package pl.touk.nussknacker.engine.management.sample.helper

import pl.touk.nussknacker.engine.api.{Documentation, HideToString, ParamName}

import java.time.LocalDateTime

object DateProcessHelper extends HideToString {

  @Documentation(
    description = "Returns current time in milliseconds"
  )
  def nowTimestamp(): Long = System.currentTimeMillis()

  @Documentation(description =
    "Just parses a date.\n" +
      "Lorem ipsum dolor sit amet enim. Etiam ullamcorper. Suspendisse a pellentesque dui, non felis. Maecenas malesuada elit lectus felis, malesuada ultricies. Curabitur et ligula"
  )
  def parseDate(@ParamName("dateString") dateString: String): LocalDateTime = {
    LocalDateTime.parse(dateString)
  }

  def noDocsMethod(date: Any, format: String): String = {
    ""
  }

  def paramsOnlyMethod(@ParamName("number") number: Int, @ParamName("format") format: String): String = {
    ""
  }

}
