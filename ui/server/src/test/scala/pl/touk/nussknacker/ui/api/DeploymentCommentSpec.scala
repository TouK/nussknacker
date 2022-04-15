package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import org.scalatest._

/**
  * TODO: On resource tests we should verify permissions and encoded response data. All business logic should be tested at ProcessServiceDb.
  */
class DeploymentCommentSpec extends FunSuite with Matchers {

  val mockDeploySettings: DeploySettings = DeploySettings(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = "issues/1234")

  val validComment = "issues/123123"
  val invalidComment = "invalid_comment"

  test("Comment validation for valid comment") {
    val deploymentComment = DeploymentComment(validComment, Some(mockDeploySettings))
    deploymentComment shouldEqual Valid(_: DeploymentComment)
  }

  test("Comment validation for invalid comment") {
    val deploymentComment = DeploymentComment(invalidComment, Some(mockDeploySettings))
    deploymentComment shouldEqual Invalid(_: CommentValidationError)
  }
}
