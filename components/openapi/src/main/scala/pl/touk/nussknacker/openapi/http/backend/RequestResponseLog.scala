package pl.touk.nussknacker.openapi.http.backend

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import sttp.client.{Request, Response, StringBody}
import sttp.model.Header

@JsonCodec(encodeOnly = true)
case class RequestResponseLog(url: String,
                              method: Option[String],
                              headers: Map[String, List[String]],
                              body: Option[String],
                              statusCode: Option[Int]) extends DisplayJsonWithEncoder[RequestResponseLog] {
  def pretty: String = {
    Map(
      "url" -> url,
      "method" -> method,
      "headers" -> headers,
      "body" -> body,
      "statusCode" -> statusCode
    ).flatMap { case (k, v) =>
      v match {
        case None => None
        case Some(vv) => Some(s"$k=$vv")
        case vv => Some(s"$k=$vv")
      }
    }.mkString(", ")
  }
}

object RequestResponseLog {

  def apply(req: Request[_, _]): RequestResponseLog = {
    val reqBody = req.body match {
      case StringBody(body, _, _) => Some(body)
      case _ => None
    }
    new RequestResponseLog(
      req.uri.toString(),
      Option(req.method.method),
      mapHeaders(req.headers),
      reqBody,
      None
    )
  }

  def apply(req: Request[_, _], resp: Response[_], resBody: Option[String]): RequestResponseLog =
    new RequestResponseLog(
      req.uri.toString(),
      None,
      mapHeaders(resp.headers),
      resBody,
      Option(resp.code.code)
    )

  private def mapHeaders(headers: Seq[Header]): Map[String, List[String]] = {
    headers
      .groupBy(_.name)
      .map { e =>
        (e._1, e._2.map(_.value).toList)
      }
  }
}
