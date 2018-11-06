package pl.touk.nussknacker.engine.dispatch

import java.nio.charset.{Charset, StandardCharsets}

import org.asynchttpclient._
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
  val asUnit: Response => Unit = _ => ()

  case class InvalidJsonResponseException(message: String) extends RuntimeException(message)

  //TODO: ability to use different charsets
  object asJson {
    def apply[Resp: DecodeJson](r: Response): Resp = {
      val response = as.String.charset(StandardCharsets.UTF_8)(r)
      response
        .decodeEither[Resp] match {
        case Left(message) => throw InvalidJsonResponseException(message)
        case Right(json) => json

      }
    }
  }

  //TODO: ability to use different charsets
  def asXml(r: Response): Elem = {
    val body = as.String.charset(StandardCharsets.UTF_8)(r)
    XML
      .withSAXParser(factory.newSAXParser)
      .loadString(body)
  }

  //TODO: ability to use different charsets
  object postJson {
    def apply[Body: EncodeJson](subject: Req, body: Body, charset: Charset = StandardCharsets.UTF_8): Req = {
      subject
        .POST
        .setContentType("application/json", StandardCharsets.UTF_8) <<
        body.asJson.spaces2
    }
  }

  //TODO: ability to use different charsets
  object postXml {
    def apply(subject: Req, body: NodeSeq): Req = {
      subject
        .POST
        .setHeader("Accept", "application/xml")
        .setContentType("application/xml", StandardCharsets.UTF_8) <<
        body.toString()
    }
  }

}