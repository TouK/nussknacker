package pl.touk.nussknacker.ui.api

import io.restassured.RestAssured.given
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.test.RestAssuredVerboseLoggingIfValidationFails
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.config.{WithBusinessCaseRestAssuredUsersExtensions, WithSimplifiedDesignerConfig}

class DictApiHttpServiceSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithBusinessCaseRestAssuredUsersExtensions
    with RestAssuredVerboseLoggingIfValidationFails {

  "The endpoint for listing available dictionaries of expected type should" - {

    "return proper empty list for expected type Integer - check subclassing" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
            | "expectedType" : {
            |     "type" : "TypedClass",
            |     "refClazzName" : "java.lang.Integer",
            |     "params":[]
            |     }
            |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts")
        .Then()
        .statusCode(200)
        .equalsJsonBody("[]")

    }

    "return proper list for expected type String" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
                    |  "expectedType" : {
                    |      "type" : "TypedClass",
                    |      "refClazzName" : "java.lang.String",
                    |      "params" : []
                    |  }
                    |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |  {
             |    "id" : "rgb",
             |    "label" : "rgb"
             |  },
             |  {
             |    "id" : "bc",
             |    "label" : "bc"
             |  },
             |  {
             |    "id" : "dict",
             |    "label" : "dict"
             |  }
             |]""".stripMargin
        )
    }

    "return proper list for expected type Long" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
                    |  "expectedType" : {
                    |      "type" : "TypedClass",
                    |      "refClazzName" : "java.lang.Long",
                    |      "params" : []
                    |  }
                    |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |  {
             |    "id" : "long_dict",
             |    "label" : "long_dict"
             |  }
             |]""".stripMargin
        )
    }

    "return proper list for expected type BigDecimal" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
                    |  "expectedType" : {
                    |      "type" : "TypedClass",
                    |      "refClazzName" : "java.math.BigInteger",
                    |      "params" : []
                    |  }
                    |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts")
        .Then()
        .statusCode(200)
        .equalsJsonBody(
          s"""[
             |  {
             |    "id" : "long_dict",
             |    "label" : "long_dict"
             |  }
             |]""".stripMargin
        )
    }

    "fail for bad request" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
                    |  "asd" : "qwerty"
                    |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/${Streaming.stringify}/dicts")
        .Then()
        .statusCode(400)
        .equalsPlainBody("Invalid value for: body (Missing required field at 'expectedType')")
    }

    "fail to return dict list for non-existing processingType" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .jsonBody("""{
                    |  "expectedType" : {
                    |      "type" : "TypedClass",
                    |      "refClazzName" : "java.lang.Long",
                    |      "params" : []
                    |  }
                    |}""".stripMargin)
        .post(s"$nuDesignerHttpAddress/api/processDefinitionData/thisProcessingTypeDoesNotExist/dicts")
        .Then()
        .statusCode(404)
        .equalsPlainBody("Processing type: thisProcessingTypeDoesNotExist not found")
    }

  }

  val existingDictId    = "rgb"
  val nonExistingDictId = "thisDictDoesNotExist"

  "The dict entry suggestion endpoint should" - {
    "return suggestions for existing prefix" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(
          s"$nuDesignerHttpAddress/api/processDefinitionData/" +
            s"${Streaming.stringify}/dicts/$existingDictId/entry?label=${"Black".take(2)}"
        )
        .Then()
        .statusCode(200)
        .equalsJsonBody("""[
                          |  {
                          |    "key" : "H000000",
                          |    "label" : "Black"
                          |  },
                          |  {
                          |    "key" : "H0000ff",
                          |    "label" : "Blue"
                          |  }
                          |]""".stripMargin)
    }

    "return 0 suggestions for non-existing prefix" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(
          s"$nuDesignerHttpAddress/api/processDefinitionData/" +
            s"${Streaming.stringify}/dicts/$existingDictId/entry?label=thisPrefixDoesNotExist"
        )
        .Then()
        .statusCode(200)
        .equalsJsonBody("""[]""".stripMargin)
    }

    "fail to return entry suggestions for non-existing dictionary" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(
          s"$nuDesignerHttpAddress/api/processDefinitionData/" +
            s"${Streaming.stringify}/dicts/$nonExistingDictId/entry?label=a"
        )
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"Dictionary with id: $nonExistingDictId not found".stripMargin)
    }

    "fail to return entry suggestions for non-existing processingType" in {
      given()
        .when()
        .basicAuthAllPermUser()
        .get(
          s"$nuDesignerHttpAddress/api/processDefinitionData/" +
            s"thisProcessingTypeDoesNotExist/dicts/$existingDictId/entry?label=a"
        )
        .Then()
        .statusCode(404)
        .equalsPlainBody(s"Processing type: thisProcessingTypeDoesNotExist not found".stripMargin)
    }
  }

}
