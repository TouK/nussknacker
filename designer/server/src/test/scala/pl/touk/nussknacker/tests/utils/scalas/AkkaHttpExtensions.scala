package pl.touk.nussknacker.tests.utils.scalas

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.Encoder
import io.circe.syntax.EncoderOps

trait AkkaHttpExtensions {

  implicit class toRequestEntity[T: Encoder](request: T) {

    def toJsonRequestEntity(): RequestEntity = {
      HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
    }

  }

}

object AkkaHttpExtensions extends AkkaHttpExtensions
