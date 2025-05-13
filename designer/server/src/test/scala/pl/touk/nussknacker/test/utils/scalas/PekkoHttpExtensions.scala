package pl.touk.nussknacker.test.utils.scalas

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}

trait PekkoHttpExtensions {

  implicit class toRequestEntity[T: Encoder](request: T) {

    def toJsonRequestEntity(): RequestEntity = {
      HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
    }

  }

}

object PekkoHttpExtensions extends PekkoHttpExtensions
