/*
 * Copyright 2015 Heiko Seeberger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.touk.esp.ui.util

import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import argonaut.{ DecodeJson, EncodeJson, Json, Parse, PrettyParams }

/**
  * Automatic to and from JSON marshalling/unmarshalling using an in-scope *Argonaut* protocol.
  *
  * To use automatic codec derivation, user needs to import `argonaut.Shapeless._`.
  */
object Argonaut62Support extends Argonaut62Support

/**
  * JSON marshalling/unmarshalling using an in-scope *Argonaut* protocol.
  *
  * To use automatic codec derivation, user needs to import `argonaut.Shapeless._`
  */
trait Argonaut62Support {

  /**
    * HTTP entity => `A`
    *
    * @param decoder decoder for `A`
    * @tparam A type to decode
    * @return unmarshaller for `A`
    */
  implicit def argonautUnmarshaller[A](implicit decoder: DecodeJson[A]): FromEntityUnmarshaller[A] =
  Unmarshaller
    .byteStringUnmarshaller
    .forContentTypes(`application/json`)
    .mapWithCharset { (data, charset) =>
      Parse.parse(data.decodeString(charset.nioCharset.name)) match {
        case Right(json)   => json
        case Left(message) => sys.error(message)
      }
    }
    .map { json =>
      decoder.decodeJson(json).result match {
        case Right(entity)            => entity
        case Left((message, history)) => sys.error(message + " - " + history.toString())
      }
    }

  /**
    * `A` => HTTP entity
    *
    * @param encoder encoder for `A`
    * @param printer pretty printer function
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit def argonautToEntityMarshaller[A](implicit encoder: EncodeJson[A], printer: Json => String = PrettyParams.nospace.pretty): ToEntityMarshaller[A] =
  Marshaller.StringMarshaller.wrap(`application/json`)(printer).compose(encoder.apply)
}