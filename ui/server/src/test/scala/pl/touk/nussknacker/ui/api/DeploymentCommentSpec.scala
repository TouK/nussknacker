package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import org.scalatest._
import pl.touk.nussknacker.ui.listener.DeploymentComment
import pl.touk.nussknacker.ui.process.deployment.{CommentValidationError, DeploymentCommentSettings, DeploymentCommentValidator, EmptyDeploymentCommentSettingsError}

class DeploymentCommentSpec extends FunSuite with Matchers {

  private val mockDeploymentCommentSettings: DeploymentCommentSettings = DeploymentCommentSettings.unsafe(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = Some("issues/1234")
  )

  private val mockDeploymentCommentSettingsWithoutExampleComment: DeploymentCommentSettings = DeploymentCommentSettings.unsafe(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = None
  )

  private val validComment = "issues/123123"
  private val invalidComment = "invalid_comment"
  private val emptyComment = None

  private val emptyValidationPattern = ""
  private val nonEmptyValidationPattern = "nonEmpty"

  test("DeploymentCommentSettings validation, with empty validationPattern") {
    DeploymentCommentSettings.create(emptyValidationPattern, None) shouldEqual Invalid(EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty."))
  }

  test("DeploymentCommentSettings validation, with non empty validationPattern") {
    DeploymentCommentSettings.create(nonEmptyValidationPattern, None) shouldEqual Valid(_: DeploymentCommentSettings)
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
    deploymentComment shouldEqual Invalid(CommentValidationError(s"Bad comment format '$invalidComment'. Example comment: ${mockDeploymentCommentSettings.exampleComment.get}."))
  }

  test("Comment validation for invalid comment without example comment") {
    val deploymentComment = DeploymentCommentValidator.createDeploymentComment(Some(invalidComment), Some(mockDeploymentCommentSettingsWithoutExampleComment))
    deploymentComment shouldEqual Invalid(CommentValidationError(s"Bad comment format '$invalidComment'."))
  }
}
