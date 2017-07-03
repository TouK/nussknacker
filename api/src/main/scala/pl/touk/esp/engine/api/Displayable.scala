package pl.touk.esp.engine.api

import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import argonaut.{EncodeJson, Json, _}

//used eg. to show variables on fe
trait Displayable {

  def originalDisplay: Option[String]

  def display : Json

}


abstract class DisplayableAsJson[T : EncodeJson] extends Displayable { self : T =>
  //tutaj przypisujemy encoder, bo przy testowaniu z UI jest jakis problem z classloaderem,
  // a jak tutaj zachlannie zaladujemy klase wygenerowana przez argonauta to wszystko dziala...
  //z tego samego powodu uzywam wszedzie `.spaces2` zamiast zrobic metode np ArgonautUtils bo rowniez jest problem z doczytaniem klasy
  val encoder = implicitly[EncodeJson[T]]
  override def display: Json = self.asInstanceOf[T].asJson(encoder)
  override def originalDisplay: Option[String] = None
}
