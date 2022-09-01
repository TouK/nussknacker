package pl.touk.nussknacker.engine.lite.requestresponse

object UrlUtils {

  val unreservedUrlCharactersRegex = "a-zA-Z0-9\\-._~"

  // This method sanitize text that will be used as a url slug. Instead of url econding we decide to use only characters
  // that are allowed in a URI, but do not have a reserved purpose: https://stackoverflow.com/a/695469 they are all described
  // as a "Unreserved Characters" in https://www.ietf.org/rfc/rfc3986.txt in section 2.3. Thanks to that slug
  // will be easier to read and remember by human
  def sanitizeUrlSlug(string: String): String =
    string.replaceAll(s"[^$unreservedUrlCharactersRegex]", "-")

 def validateUrlSlug(string: String): Boolean =
   string.matches(s"[$unreservedUrlCharactersRegex]+")

}
