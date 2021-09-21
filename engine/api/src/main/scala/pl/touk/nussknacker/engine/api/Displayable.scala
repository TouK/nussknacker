package pl.touk.nussknacker.engine.api

import io.circe.{Encoder, Json}

/** Used to show variables in UI
  * [[pl.touk.nussknacker.engine.api.DisplayJson#originalDisplay]] method can be used to show original input if needed (i.e useful for csv record),
  * so in UI variable will be pretty-printed in json format and raw-printed.
**/
trait DisplayJson {

  def asJson: Json

  def originalDisplay: Option[String]

}

abstract class DisplayJsonWithEncoder[T:Encoder] extends DisplayJson { self : T =>

  private val encoder: Encoder[T] = implicitly[Encoder[T]]

  def asJson: Json = encoder(this)

  def originalDisplay: Option[String] = None

}
