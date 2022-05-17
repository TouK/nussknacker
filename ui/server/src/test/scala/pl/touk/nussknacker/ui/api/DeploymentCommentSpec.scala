package pl.touk.nussknacker.ui.api

import cats.data.Validated.{Invalid, Valid}
import org.scalatest._
import pl.touk.nussknacker.ui.listener.{CommentValidationError, DeploySettings, DeploymentComment, EmptyDeploySettingsError}

class DeploymentCommentSpec extends FunSuite with Matchers {

  private val mockDeploySettings: DeploySettings = DeploySettings.unsafe(
    validationPattern = "(issues/[0-9]*)",
    exampleComment = "issues/1234")

  private val validComment = "issues/123123"
  private val invalidComment = "invalid_comment"
  private val emptyComment = None

  private val emptyValidationPattern = ""
  private val emptyExampleComment = ""
  private val nonEmptyValidationPattern = "nonEmpty"
  private val nonEmptyExampleComment = "nonEmpty"

  test("DeploySettings validation, should allow non empty strings") {
    DeploySettings(emptyValidationPattern ,emptyExampleComment) shouldEqual Invalid(EmptyDeploySettingsError("Fields validationPattern and exampleComment both cannot be empty."))
  }

  test("DeploySettings validation, should not allow empty fields") {
    DeploySettings(nonEmptyValidationPattern, nonEmptyExampleComment) shouldEqual Valid(_: DeploySettings)
  }

  test("Comment validation when comment not required") {
    DeploymentComment.validateDeploymentComment(Some(validComment), None) shouldEqual Valid(_: DeploymentComment)
  }

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
    deploymentComment shouldEqual Invalid(CommentValidationError(s"Bad comment format '$invalidComment'. Example comment: ${mockDeploySettings.exampleComment}."))
  }
}
