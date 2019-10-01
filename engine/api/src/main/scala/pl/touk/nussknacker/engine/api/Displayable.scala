package pl.touk.nussknacker.engine.api

import argonaut.Argonaut._
import argonaut.{EncodeJson, Json, _}

/** Used to show variables in UI
  * [[pl.touk.nussknacker.engine.api.Displayable#originalDisplay]] method can be used to show original input if needed (i.e useful for csv record),
  * so in UI variable will be pretty-printed in json format and raw-printed.
**/
trait Displayable {

  def originalDisplay: Option[String]

  def display : Json

}


abstract class DisplayableAsJson[T : EncodeJson] extends Displayable { self : T =>
  //eager encoder loading due to some classloading issues
  private val encoder: EncodeJson[T] = implicitly[EncodeJson[T]]
  override def display: Json = self.asInstanceOf[T].asJson(encoder)
  override def originalDisplay: Option[String] = None
}
