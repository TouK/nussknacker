package pl.touk.nussknacker.http.enricher

import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.json.JsonUtils
import pl.touk.nussknacker.http.enricher.HttpEnricher.Body
import pl.touk.nussknacker.http.enricher.MapExtensions.MapToHashMapExtension
import sttp.client3.Response
import sttp.model.MediaType

private[enricher] object HttpEnricherOutput {

  // TODO: add typing results with values if evaulable
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
          "headers"    -> Typed.genericTypeClass[Map[_, _]](Typed[String] :: Typed[String] :: Nil),
          "body"       -> Unknown
        )
      ),
    )
  )

  // TODO: filter out configured seucurities
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
          case s if s.toLowerCase.contains(MediaType.ApplicationJson.toString()) =>
            io.circe.parser.parse(body) match {
              case Right(json) => JsonUtils.jsonToAny(json)
              case Left(err) =>
                throw NonTransientException(
                  input = body,
                  message = s"Could not parse json: ${err.message}",
                  cause = err.underlying
                )
            }
          case s if s.toLowerCase.contains(MediaType.TextPlain.toString()) => body
          /*
            TODO decision: if we get an unsupported body type:
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
