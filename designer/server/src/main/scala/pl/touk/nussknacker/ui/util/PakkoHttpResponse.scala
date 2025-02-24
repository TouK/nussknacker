package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.model.headers.ContentDispositionTypes
import org.apache.pekko.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes, headers}

object PakkoHttpResponse {

  def asFile(entity: ResponseEntity, fileName: String) = {
    HttpResponse(
      status = StatusCodes.OK,
      entity = entity,
      headers = List(headers.`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> fileName)))
    )
  }

}
