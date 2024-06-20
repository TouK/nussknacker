package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class DetermineOfferedPlanSpec
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures {

  "Properly determine offers for customers" in {
    val customers = List(
      customerJson("Nick", age = 15, gender = "Male", isBigSpender = false),
      customerJson("John", age = 25, gender = "Male", isBigSpender = false),
      customerJson("Nicole", age = 35, gender = "Female", isBigSpender = true),
      customerJson("Michael", age = 67, gender = "Male", isBigSpender = false),
    )

    customers.foreach { customer =>
      sendMessageToKafka("Customers", customer)
    }

    eventually {
      val smses = readAllMessagesFromKafka("SmsesWithOffer")
      smses should equal(
        List(
          smsWithOfferJson("Nick", "Junior Package"),
          smsWithOfferJson("Michael", "Senior Citizen Plan")
        )
      )
    }
  }

  override protected def afterEach(): Unit = {
    purgeKafkaTopic("Customers")
    purgeKafkaTopic("SmsesWithOffer")
    super.afterEach()
  }

  private def customerJson(name: String, age: Int, gender: String, isBigSpender: Boolean) = {
    ujson.read(s"""
         |{
         |  "name": "$name",
         |  "age": $age,
         |  "gender": "$gender",
         |  "isBigSpender": $isBigSpender
         |}
         |""".stripMargin)
  }

  private def smsWithOfferJson(name: String, offer: String) = {
    ujson.read(s"""
         |{
         |  "name": "$name",
         |  "assignedOffer": "$offer"
         |}
         |""".stripMargin)
  }

}
