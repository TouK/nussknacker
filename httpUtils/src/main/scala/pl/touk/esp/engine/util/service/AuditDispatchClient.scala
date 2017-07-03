package pl.touk.esp.engine.util.service

import java.util.concurrent.atomic.AtomicInteger

import argonaut._
import argonaut.Argonaut._
import com.ning.http.client.Response
import dispatch._

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Elem, XML}

trait AuditDispatchClient extends RequestResponseLogging {

  protected def http: Http

  protected lazy val clientId = UniqueIdentifier()

  // JSON

  def postObjectAsJson[Body: EncodeJson, Resp: DecodeJson](url: Req, body: Body)
                                                          (implicit executionContext: ExecutionContext,
                                                           logCorrelationId: LogCorrelationId): Future[Resp] = {
    val req = preparePostJsonRequest(url, body)
    sendWithAuditAndStatusChecking(req).map {
      _.decodeWithMessage[Resp, Resp](identity, msg => throwWithLogging(req, new InvalidJsonResponseException(msg)))
    }
  }

  def postObjectAsJsonWithoutResponseParsing[Body: EncodeJson](url: Req, body: Body)
                                                              (implicit executionContext: ExecutionContext,
                                                               logCorrelationId: LogCorrelationId): Future[String] = {
    val req = preparePostJsonRequest(url, body)
    sendWithAuditAndStatusChecking(req)
  }

  def preparePostJsonRequest[Body: EncodeJson](url: Req, body: Body): Req = {
    val bodyAsString = body.asJson.spaces2
    val req = url.setContentType("application/json", "utf-8") << bodyAsString
    req
  }

  def getJsonAsObject[Resp: DecodeJson](req: Req)
                                       (implicit executionContext: ExecutionContext,
                                        logCorrelationId: LogCorrelationId): Future[Resp] = {
    sendWithAuditAndStatusChecking(req).map {
      _.decodeWithMessage[Resp, Resp](identity, msg => throwWithLogging(req, new InvalidJsonResponseException(msg)))
    }
  }

  def getPossiblyUnavailableJsonAsObject[Resp: DecodeJson](req: Req)
                                                          (implicit executionContext: ExecutionContext,
                                                           logCorrelationId: LogCorrelationId): Future[Option[Resp]] = {
    sendWithAudit(req).map { resp =>
      if (resp.getStatusCode / 100 == 2) {
        val decoded = resp
          .getResponseBody
          .decodeWithMessage[Resp, Resp](identity, msg => throwWithLogging(req, new InvalidJsonResponseException(msg)))
        Some(decoded)
      } else if (resp.getStatusCode == 404) {
        None
      } else {
        throwWithLogging(req, StatusCode(resp.getStatusCode))
      }
    }
  }

  def getJson(req: Req)
             (implicit executionContext: ExecutionContext,
              logCorrelationId: LogCorrelationId): Future[Json] = {
    sendWithAuditAndStatusChecking(req).map { respAsString =>
      respAsString.parseWith[Json](identity, msg => throwWithLogging(req, new InvalidJsonResponseException(msg)))
    }
  }

  // XML

  def getXml(req: Req)
            (implicit executionContext: ExecutionContext,
             logCorrelationId: LogCorrelationId): Future[Elem] = {
    sendWithAuditAndStatusChecking(req).map(XML.withSAXParser(AuditDispatchClient.factory.newSAXParser).loadString)
  }

  def postXmlApp(url: Req, xml: Elem)
                (implicit executionContext: ExecutionContext,
                 logCorrelationId: LogCorrelationId): Future[Elem] = {
    val contentType = "application/xml"
    postXml(url, xml, contentType)
  }

  def postXmlText(url: Req, xml: Elem)
                 (implicit executionContext: ExecutionContext,
                  logCorrelationId: LogCorrelationId): Future[Elem] = {
    val contentType = "text/xml"
    postXml(url, xml, contentType)
  }

  private def postXml(url: Req, xml: Elem, contentType: String)
                     (implicit executionContext: ExecutionContext,
                      logCorrelationId: LogCorrelationId): Future[Elem] = {
    val bodyAsString = xml.toString()
    val req = url
      .POST
      .setHeader("Accept", "application/xml")
      .setContentType(contentType, "UTF-8")
      .setBody(bodyAsString)

    sendWithAuditAndStatusChecking(req)
      .map(XML.withSAXParser(AuditDispatchClient.factory.newSAXParser).loadString)
  }

  // LOW LEVEL

  def getWithAudit(req: Req)(implicit executionContext: ExecutionContext,
                             logCorrelationId: LogCorrelationId): Future[String] = {
    sendWithAuditAndStatusChecking(req.GET)
  }

  def postWithParamsAndWithAudit(req: Req, params: Map[String, String])
                                (implicit executionContext: ExecutionContext,
                                 logCorrelationId: LogCorrelationId): Future[String] = {
    val request = req.POST << params
    sendWithAuditAndStatusChecking(request)
  }

  def putWithParamsAndWithAudit(req: Req, params: Map[String, String])
                               (implicit executionContext: ExecutionContext,
                                logCorrelationId: LogCorrelationId): Future[String] = {
    val request = req.PUT << params
    sendWithAuditAndStatusChecking(request)
  }

  // BASE

  def sendWithAuditAndStatusChecking(req: Req)
                                    (implicit executionContext: ExecutionContext,
                                     logCorrelationId: LogCorrelationId): Future[String] = {
    sendWithAudit(req).map(checkStatusThanConvertToString(req))
  }

  protected def sendWithAuditWithResponseAsString(req: Req)
                                                 (implicit executionContext: ExecutionContext,
                                                  logCorrelationId: LogCorrelationId): Future[String] = {
    sendWithAudit(req).map(_.getResponseBody)
  }

  def sendWithAudit(req: Req)
                   (implicit executionContext: ExecutionContext,
                    logCorrelationId: LogCorrelationId): Future[Res] = {
    logRequestResponse(req)(logCorrelationId.withClientId(clientId)) {
      http(req > identity[Res] _)
    }
  }

  private def checkStatusThanConvertToString(req: Req)(resp: Response)(implicit logCorrelationId: LogCorrelationId) = {
    if (resp.getStatusCode / 100 != 2) {
      throwWithLogging(req, StatusCode(resp.getStatusCode))
    }
    resp.getResponseBody
  }

  def shutdown(): Unit = {
    http.shutdown()
  }

  private def throwWithLogging(req: Req, ex: Throwable)(implicit logCorrelationId: LogCorrelationId) = {
    logFailure(req, logCorrelationId.withClientId(clientId), ex)
    throw ex
  }

}

class AuditDispatchClientImpl(override protected val http: Http) extends AuditDispatchClient

object AuditDispatchClient {
  def apply(http: Http): AuditDispatchClient =
    new AuditDispatchClientImpl(http)

  private lazy val factory = {
    val spf = javax.xml.parsers.SAXParserFactory.newInstance()
    spf.setNamespaceAware(false)
    spf
  }
}

object UniqueIdentifier {

  private val id = new AtomicInteger(0)

  def apply(): Int = {
    id.incrementAndGet()
  }

}

case class LogCorrelationId(private val id: String) {
  def withClientId(clientId: Int) = id + "-" + clientId
}

class InvalidJsonResponseException(message: String) extends Exception(message)