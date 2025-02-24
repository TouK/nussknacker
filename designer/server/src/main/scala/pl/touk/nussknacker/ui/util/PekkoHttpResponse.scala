package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.model.{headers, HttpResponse, ResponseEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes

object PekkoHttpResponse {

  def asFile(entity: ResponseEntity, fileName: String): HttpResponse = {
    HttpResponse(
      status = StatusCodes.OK,
      entity = entity,
      headers = List(headers.`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> fileName)))
    )
  }

}
