package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.ui.listener.Comment


case class UserComment(comment: String) extends Comment {
  override def value: String = comment
}
