package pl.touk.nussknacker.ui.server

object HeadersSupport {
  final case class FileName(value: String)

  final case class ContentDisposition(fileName: Option[FileName]) {
    private val doubleQuote = '"'
    def headerValue(): Option[String] =
      fileName.map(v => s"Content-Disposition: attachment; filename=$doubleQuote${v.value}$doubleQuote")
  }

  object ContentDisposition {
    private val filenameRegex = "filename=\"(.+?)\"".r

    def apply(headerValue: String): ContentDisposition =
      new ContentDisposition(
        filenameRegex
          .findFirstMatchIn(headerValue)
          .map(_.group(1))
          .map(FileName)
      )

    def fromFileNameString(fileName: String): ContentDisposition =
      new ContentDisposition(Some(FileName(fileName)))
  }

  final case class ForwardedUsername(value: String)

  object ForwardedUsername {
    val headerName = "forwarded-username"
  }

}
