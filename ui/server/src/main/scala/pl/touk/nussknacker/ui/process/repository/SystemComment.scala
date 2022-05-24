package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.ui.listener.Comment

case class SystemComment(value: String) extends Comment

case class UpdateProcessComment(override val value: String) extends SystemComment
