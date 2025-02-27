package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.model.{headers, HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.model.headers.ContentDispositionTypes

object AkkaHttpResponse {

  def asFile(entity: ResponseEntity, fileName: String) = {
    HttpResponse(
      status = StatusCodes.OK,
      entity = entity,
      headers = List(headers.`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> fileName)))
    )
  }

}
