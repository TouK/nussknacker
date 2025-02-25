package pl.touk.nussknacker.test.utils.scalas

import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, RequestEntity}
import io.circe.Encoder
import io.circe.syntax.EncoderOps

trait PekkoHttpExtensions$ {

  implicit class toRequestEntity[T: Encoder](request: T) {

    def toJsonRequestEntity(): RequestEntity = {
      HttpEntity(ContentTypes.`application/json`, request.asJson.spaces2)
    }

  }

}

object PekkoHttpExtensions$ extends PekkoHttpExtensions$
