package pl.touk.nussknacker.engine.api

case class TemplateEvaluationResult(renderedParts: List[TemplateRenderedPart]) {
  def renderedTemplate: String = renderedParts.map(_.value).mkString("")
}

sealed trait TemplateRenderedPart {
  def value: String
}

object TemplateRenderedPart {
  case class RenderedLiteral(value: String) extends TemplateRenderedPart

  case class RenderedSubExpression(value: String) extends TemplateRenderedPart
}
