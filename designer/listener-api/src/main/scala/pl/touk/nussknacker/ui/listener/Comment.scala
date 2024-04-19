package pl.touk.nussknacker.ui.listener

abstract class Comment {
  def value: String

  override def toString: String = value
}
