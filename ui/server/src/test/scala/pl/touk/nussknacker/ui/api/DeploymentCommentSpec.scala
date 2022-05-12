package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import org.scalatest._
import pl.touk.nussknacker.ui.listener.{CommentValidationError, DeploySettings, DeploymentComment}

class DeploymentCommentSpec extends FunSuite with Matchers {

  private val mockDeploySettings: DeploySettings = DeploySettings(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = "issues/1234")

  private val validComment = "issues/123123"
  private val invalidComment = "invalid_comment"
  private val emptyComment = None

  test("Comment required but got empty") {
    val deploymentComment = DeploymentComment.validateDeploymentComment(emptyComment, Some(mockDeploySettings))
    deploymentComment shouldEqual Invalid(CommentValidationError("Comment is required."))
  }

  test("Comment validation for valid comment") {
    val deploymentComment = DeploymentComment(validComment, Some(mockDeploySettings))
    deploymentComment shouldEqual Valid(_: DeploymentComment)
  }

  test("Comment validation for invalid comment") {
    val deploymentComment = DeploymentComment(invalidComment, Some(mockDeploySettings))
    deploymentComment shouldEqual Invalid(_: CommentValidationError)
  }
}
