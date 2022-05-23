package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import org.scalatest._
import pl.touk.nussknacker.ui.listener.DeploymentComment
import pl.touk.nussknacker.ui.validation.{CommentValidationError, DeploymentCommentSettings, DeploymentCommentValidator, EmptyDeploymentCommentSettingsError}

class DeploymentCommentSpec extends FunSuite with Matchers {

  private val mockDeploymentCommentSettings: DeploymentCommentSettings = DeploymentCommentSettings.unsafe(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = "issues/1234")

  private val validComment = "issues/123123"
  private val invalidComment = "invalid_comment"
  private val emptyComment = None

  private val emptyValidationPattern = ""
  private val emptyExampleComment = ""
  private val nonEmptyValidationPattern = "nonEmpty"
  private val nonEmptyExampleComment = "nonEmpty"

  test("DeploymentCommentSettings validation, should allow non empty strings") {
    DeploymentCommentSettings.create(emptyValidationPattern ,emptyExampleComment) shouldEqual Invalid(EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty."))
  }

  test("DeploymentCommentSettings validation, should not allow empty fields") {
    DeploymentCommentSettings.create(nonEmptyValidationPattern, nonEmptyExampleComment) shouldEqual Valid(_: DeploymentCommentSettings)
  }

  test("Comment not required, should pass validation for any comment") {
    DeploymentCommentValidator.createDeploymentComment(Some(validComment), None) shouldEqual Valid(_: DeploymentComment)
  }

  test("Comment required but got empty, should fail validation") {
    val deploymentComment = DeploymentCommentValidator.createDeploymentComment(emptyComment, Some(mockDeploymentCommentSettings))
    deploymentComment shouldEqual Invalid(CommentValidationError("Comment is required."))
  }

  test("Comment validation for valid comment") {
    val deploymentComment = DeploymentCommentValidator.createDeploymentComment(Some(validComment), Some(mockDeploymentCommentSettings))
    deploymentComment shouldEqual Valid(_: DeploymentComment)
  }

  test("Comment validation for invalid comment") {
    val deploymentComment = DeploymentCommentValidator.createDeploymentComment(Some(invalidComment), Some(mockDeploymentCommentSettings))
    deploymentComment shouldEqual Invalid(CommentValidationError(s"Bad comment format '$invalidComment'. Example comment: ${mockDeploymentCommentSettings.exampleComment}."))
  }
}
