package pl.touk.nussknacker.engine.dispatch

import com.ning.http.client
import dispatch.{Req, as}

import scala.xml.{Elem, NodeSeq, XML}

object utils {

  import argonaut._
  import Argonaut._


  private lazy val factory = {
    val spf = javax.xml.parsers.SAXParserFactory.newInstance()
    spf.setNamespaceAware(false)
    spf
  }
  val asUnit: (client.Response => Unit) = _ => ()

  case class InvalidJsonResponseException(message: String) extends RuntimeException(message)

  object asJson {
    def apply[Resp: DecodeJson](r: client.Response): Resp = {
      val response = as.String(r)
      response
        .decodeOption[Resp]
        .getOrElse(throw InvalidJsonResponseException(response))
    }
  }

  object asXml {
    def apply(r: client.Response): Elem = {
      val body = as.String(r)
      XML
        .withSAXParser(factory.newSAXParser)
        .loadString(body)
    }
  }

  object postJson {
    def apply[Body: EncodeJson](subject: Req, body: Body): Req = {
      subject
        .POST
        .setContentType("application/json", "utf-8") <<
        body.asJson.spaces2
    }
  }

  object postXml {
    def apply(subject: Req, body: NodeSeq): Req = {
      subject
        .POST
        .setHeader("Accept", "application/xml")
        .setContentType("application/xml", "UTF-8") <<
        body.toString()
    }
  }

}