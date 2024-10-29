package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.sql.{Date, Time, Timestamp}

class FlinkBaseTypeInfoRegisterTest extends AnyFunSuite with Matchers {

  private val reqByNuTypesMapping: Map[Class[_], TypeInformation[_]] = Map(
    classOf[LocalDate]     -> Types.LOCAL_DATE,
    classOf[LocalTime]     -> Types.LOCAL_TIME,
    classOf[LocalDateTime] -> Types.LOCAL_DATE_TIME,
  )

  private val otherTypesMapping: Map[Class[_], TypeInformation[_]] = Map(
    classOf[Instant]   -> Types.INSTANT,
    classOf[Date]      -> Types.SQL_DATE,
    classOf[Time]      -> Types.SQL_TIME,
    classOf[Timestamp] -> Types.SQL_TIMESTAMP,
  )

  test("Looking for TypeInformation for a base klass should return a GenericTypeInfo") {
    FlinkBaseTypeInfoRegister.baseTypes.foreach { base =>
      val typeInfo = TypeInformation.of(base.klass)
      typeInfo shouldBe new GenericTypeInfo(base.klass)
    }
  }

  test("Looking for TypeInformation for a base klass with register should return a SpecificTypeInformation") {
    FlinkBaseTypeInfoRegister.makeSureBaseTypesAreRegistered()

    FlinkBaseTypeInfoRegister.baseTypes.foreach { base =>
      val typeInfo = TypeInformation.of(base.klass)
      Some(typeInfo) shouldBe reqByNuTypesMapping.get(base.klass)
    }
  }

  test("Verify TypeInformation.of for other types") {
    otherTypesMapping.foreach { case (klass, expected) =>
      val typeInfo = TypeInformation.of(klass)
      typeInfo shouldBe expected
    }
  }

}
