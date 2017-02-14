package pl.touk.esp.engine.api

//used eg. to show variables on fe
trait Displayable {

  def prettyDisplay: String
  def originalDisplay: Option[String]

}
