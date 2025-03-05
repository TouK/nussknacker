package pl.touk.nussknacker.test

import io.circe.{parser, ACursor, Json}
import pl.touk.nussknacker.test.WithSttpTestUtils.ResponseOps

import scala.language.implicitConversions

trait WithSttpTestUtils {

  implicit def toResponseOps(response: sttp.client3.Response[String]): ResponseOps = new ResponseOps(response)
}

object WithSttpTestUtils {

  implicit class ResponseOps(val response: sttp.client3.Response[String]) {

    def extractFieldJsonValue(field: String, fields: String*): Json = {
      val result = for {
        json <- parser.parse(response.body).toOption
        cursor = json.hcursor
        jsonFromField <- (field :: fields.toList)
          .foldLeft(cursor: ACursor) { case (cur, field) => cur.downField(field) }
          .focus
      } yield jsonFromField
      result.getOrElse(
        throw new IllegalArgumentException(s"Cannot extract value of field [$field] from JSON [${response.body}]")
      )
    }

    def bodyAsJson: Json = {
      io.circe.parser
        .parse(response.body)
        .toOption
        .getOrElse(throw new IllegalArgumentException(s"Cannot create JSON from [${response.body}]"))
    }

  }

}
