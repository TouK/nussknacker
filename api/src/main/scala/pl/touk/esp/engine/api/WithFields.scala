package pl.touk.esp.engine.api

trait WithFields extends Displayable {

  def separator = "|"

  def fields: List[Any]

  override def display = fields.map(a => Option(a).getOrElse("")).mkString(separator)

}
