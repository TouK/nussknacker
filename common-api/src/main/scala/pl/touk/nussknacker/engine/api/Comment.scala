package pl.touk.nussknacker.engine.api

class Comment private (val content: String) extends AnyVal {
  override def toString: String = content
}

object Comment {

  def from(content: String): Option[Comment] = {
    if (content.trim.nonEmpty) Some(new Comment(content)) else None
  }

  def unsafeFrom(content: String): Comment = {
    from(content).getOrElse(throw new IllegalArgumentException("Comment content cannot be empty"))
  }

}
