package pl.touk.nussknacker.ui.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ApiAdapterServiceSpec extends AnyFlatSpec with Matchers {

  class TestApiAdapterService extends ApiAdapterService[TestVersionedApi] {
    override def getAdapters: Map[Int, ApiAdapter[TestVersionedApi]] = TestVersionedApiAdapters.adapters
  }

  val testApiAdapterService: TestApiAdapterService = new TestApiAdapterService()

  val v1: TestVersionedApiV1 = TestVersionedApiV1(1, "foo", List("bar1", "baar2", "baaar3"))
  val v2: TestVersionedApiV2 =
    TestVersionedApiV2(2, "foo", List("bar1", "baar2", "baaar3"), "v1->v2 autogen. description")
  val v3: TestVersionedApiV3 =
    TestVersionedApiV3(3, Some("foo"), List("bar1", "baar2", "baaar3"), "v1->v2 autogen. description")
  val v4: TestVersionedApiV4 = TestVersionedApiV4(4, None, List(4, 5, 6), "v1->v2 autogen. description")

  it should "adapt up from V1 to V2" in {
    val lifted = testApiAdapterService.adaptUp(v1, 1)
    lifted shouldBe Right(v2)
  }

  it should "adapt up from V2 to V3" in {
    val adapted = testApiAdapterService.adaptUp(v2, 1)
    adapted shouldBe Right(v3)
  }

  it should "adapt up from V3 to V4" in {
    val adapted = testApiAdapterService.adaptUp(v3, 1)
    adapted shouldBe Right(v4)
  }

  it should "adapt up from V1 to V3" in {
    val adapted = testApiAdapterService.adaptUp(v1, 2)
    adapted shouldBe Right(v3)
  }

  it should "adapt up from V1 to V4" in {
    val adapted = testApiAdapterService.adaptUp(v1, 3)
    adapted shouldBe Right(v4)
  }

  it should "adapt up from V2 to V4" in {
    val adapted = testApiAdapterService.adaptUp(v2, 2)
    adapted shouldBe Right(v4)
  }

  it should "adapt down from V2 to V1" in {
    val downgraded = testApiAdapterService.adaptDown(v2, 1)
    downgraded shouldBe Right(v1)
  }

  it should "adapt down from V3 to V2" in {
    val downgraded = testApiAdapterService.adaptDown(v3, 1)
    downgraded shouldBe Right(v2)
  }

  it should "adapt down from V4 to V3" in {
    val downgraded = testApiAdapterService.adaptDown(v4, 1)
    downgraded shouldBe Right(v3.copy(foo = None))
  }

  it should "adapt down from V3 to V1" in {
    val downgraded = testApiAdapterService.adaptDown(v3, 2)
    downgraded shouldBe Right(v1)
  }

  it should "adapt down from V4 to V1" in {
    val downgraded = testApiAdapterService.adaptDown(v4, 3)
    downgraded shouldBe Right(v1.copy(foo = "<<null>>"))
  }

  it should "adapt down from V4 to V2" in {
    val downgraded = testApiAdapterService.adaptDown(v4, 2)
    downgraded shouldBe Right(v2.copy(foo = "<<null>>"))
  }

  it should "fail with Left when tried adapt too high" in {
    val adapted = testApiAdapterService.adaptUp(v2, 3)
    adapted shouldBe Left(OutOfRangeAdapterRequestError(currentVersion = 4, noOfVersionsLeftToApply = 1))
  }

  it should "fail with Left when tried adapt too low" in {
    val adapted = testApiAdapterService.adaptDown(v2, 3)
    adapted shouldBe Left(OutOfRangeAdapterRequestError(currentVersion = 1, noOfVersionsLeftToApply = -2))
  }

}

sealed trait TestVersionedApi extends VersionedData

case class TestVersionedApiV1(version: Int, foo: String, bar: List[String]) extends TestVersionedApi {
  override def currentVersion(): Int = version
}

//add description field
case class TestVersionedApiV2(version: Int, foo: String, bar: List[String], description: String)
    extends TestVersionedApi {
  override def currentVersion(): Int = version
}

//making foo optional
case class TestVersionedApiV3(version: Int, foo: Option[String], bar: List[String], description: String)
    extends TestVersionedApi {
  override def currentVersion(): Int = version
}

//change bar type from List[String] to List[Int] and set foo to None
case class TestVersionedApiV4(version: Int, foo: Option[String], bar: List[Int], description: String)
    extends TestVersionedApi {
  override def currentVersion(): Int = version
}

object TestVersionedApiAdapters {

  case object TestVersionedApiV1ToV2 extends ApiAdapter[TestVersionedApi] {

    override def liftVersion: TestVersionedApi => TestVersionedApi = {
      case v1: TestVersionedApiV1 =>
        TestVersionedApiV2(
          version = v1.version + 1,
          foo = v1.foo,
          bar = v1.bar,
          description = "v1->v2 autogen. description"
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

    override def downgradeVersion: TestVersionedApi => TestVersionedApi = {
      case v2: TestVersionedApiV2 =>
        TestVersionedApiV1(
          version = v2.version - 1,
          foo = v2.foo,
          bar = v2.bar
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

  }

  case object TestVersionedApiV2ToV3 extends ApiAdapter[TestVersionedApi] {

    override def liftVersion: TestVersionedApi => TestVersionedApi = {
      case v2: TestVersionedApiV2 =>
        TestVersionedApiV3(
          version = v2.version + 1,
          foo = Option[String](v2.foo),
          bar = v2.bar,
          description = v2.description
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

    override def downgradeVersion: TestVersionedApi => TestVersionedApi = {
      case v3: TestVersionedApiV3 =>
        TestVersionedApiV2(
          version = v3.version - 1,
          foo = v3.foo.fold[String]("<<null>>")(identity),
          bar = v3.bar,
          description = v3.description
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

  }

  case object TestVersionedApiV3ToV4 extends ApiAdapter[TestVersionedApi] {

    override def liftVersion: TestVersionedApi => TestVersionedApi = {
      case v3: TestVersionedApiV3 =>
        TestVersionedApiV4(
          version = v3.version + 1,
          foo = None,
          bar = v3.bar.map(_.length),
          description = v3.description
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

    override def downgradeVersion: TestVersionedApi => TestVersionedApi = {
      case v4: TestVersionedApiV4 =>
        TestVersionedApiV3(
          version = v4.version - 1,
          foo = v4.foo,
          bar = v4.bar.map(i => "b" + "a".repeat(i - 3) + "r" + (i - 3).toString),
          description = v4.description
        )
      case _ => throw new IllegalStateException("Expecting another value object")
    }

  }

  val adapters: Map[Int, ApiAdapter[TestVersionedApi]] =
    Map(1 -> TestVersionedApiV1ToV2, 2 -> TestVersionedApiV2ToV3, 3 -> TestVersionedApiV3ToV4)
}
