package pl.touk.nussknacker.engine.api

class Comment private (val content: String) extends AnyVal {
  override def toString: String = content
}

object Comment {

  def from(content: String): Option[Comment] = {
    val trimmedContent = content.trim
    if (trimmedContent.nonEmpty) Some(new Comment(trimmedContent)) else None
  }

  def unsafeFrom(content: String): Comment = {
    from(content).getOrElse(throw new IllegalArgumentException("Comment content cannot be empty"))
  }

}
