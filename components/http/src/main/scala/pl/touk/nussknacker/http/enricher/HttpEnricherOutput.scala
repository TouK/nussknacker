package pl.touk.nussknacker.http.enricher

import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.json.JsonUtils
import pl.touk.nussknacker.http.enricher.HttpEnricher.Body
import pl.touk.nussknacker.http.enricher.MapExtensions.MapToHashMapExtension
import sttp.client3.Response
import sttp.model.MediaType

// TODO decision: can we leak headers / url / body in scenario? would it be enough to filter out configured securities?
private[enricher] object HttpEnricherOutput {

  // TODO: fill out request typing result with values determined at validation
  def typingResult(requestBodyTypingResult: TypingResult): TypedObjectTypingResult = Typed.record(
    List(
      "request" -> Typed.record(
        List(
          "url"     -> Typed[String],
          "method"  -> Typed[String],
          "headers" -> Typed.typedClass[java.util.Map[String, String]],
          "body"    -> requestBodyTypingResult
        )
      ),
      "response" -> Typed.record(
        List(
          "statusCode" -> Typed[Int],
          "statusText" -> Typed[String],
          "headers"    -> Typed.typedClass[java.util.Map[String, String]],
          "body"       -> Unknown
        )
      ),
    )
  )

  def buildOutput(response: Response[Either[String, String]], requestBody: Option[Body]): java.util.Map[String, _] =
    Map(
      "request" -> Map(
        "url"    -> response.request.uri.toString(),
        "method" -> response.request.method.method,
        "headers" -> response.request.headers
          .map { h =>
            h.name -> h.value
          }
          .toMap
          .toHashMap,
        "body" -> requestBody.map(_.value).orNull
      ).toHashMap,
      "response" -> Map(
        "statusCode" -> response.code.code,
        "statusText" -> response.statusText,
        "headers" ->
          response.headers
            .map { h =>
              h.name -> h.value
            }
            .toMap
            .toHashMap,
        "body" -> parseBody(response)
      ).toHashMap
    ).toHashMap

  private def parseBody(response: Response[Either[String, String]]) = {
    response.contentType match {
      case Some(contentType) => {
        val body = response.body match {
          // TODO decision: error handling strategy - what to do if returned not 2xx?
          case Left(value)  => value
          case Right(value) => value
        }
        contentType match {
          case s if s == MediaType.ApplicationJson.toString() =>
            io.circe.parser.parse(body) match {
              case Right(json) => JsonUtils.jsonToAny(json)
              case Left(err) =>
                throw NonTransientException(
                  input = body,
                  message = s"Could not parse json: ${err.message}",
                  cause = err.underlying
                ) // TODO decision: if we cant parse - throw exception or return null?
            }
          case s if s == MediaType.TextPlain.toString() => body
          /*
            TODO decision: if we cant parse body, do we:
             1. treat it as text/plain - pass it as string without parsing
             2. throw exception
             3. return null
           */
          case _ => body
        }
      }
      case None => null
    }
  }

}
