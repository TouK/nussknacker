package pl.touk.nussknacker.engine.api

trait WithFields extends Displayable {

  def separator = "|"

  def fields: List[Any]

  override def originalDisplay: Option[String] = Some(fields.map(a => Option(a).getOrElse("")).mkString(separator))
}
