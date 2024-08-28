package pl.touk.nussknacker.engine.flink.table.join

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.types.Row
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, LoneElement}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.{FlinkTableComponentProvider, SpelValues}
import pl.touk.nussknacker.engine.flink.table.join.TableJoinTest.OrderOrProduct
import pl.touk.nussknacker.engine.flink.table.join.TableJoinTest.OrderOrProduct._
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class TableJoinTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with Inside
    with ValidatedValuesDetailedMessage
    with LoneElement {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] =
    FlinkTableComponentProvider.configIndependentComponents ::: Nil

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExecutionMode(ExecutionMode.Batch)
    .withExtraComponents(additionalComponents)
    .build()

  private val mainBranchId   = "main"
  private val joinedBranchId = "joined"

  private val localPreJoinVariableName = "localPreJoin"

  private val joinNodeId = "joined-node-id"

  private val someProduct    = OrderOrProduct.createProduct(1, "Foo product")
  private val anotherProduct = OrderOrProduct.createProduct(2, "Bar product")
  private val delayedProduct = OrderOrProduct.createProduct(3, "Delayed product")

  private val orderReferringToExistingProduct = OrderOrProduct.createOrder(10, someProduct.id)

  private val nonExistingProductId               = 100
  private val orderReferringToNonExistingProduct = OrderOrProduct.createOrder(20, nonExistingProductId)

  private val orderReferringToDelayedProduct = OrderOrProduct.createOrder(30, delayedProduct.id)

  test("should inner join stream") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(JoinType.INNER),
      List(
        someProduct,
        anotherProduct,
        orderReferringToExistingProduct,
        orderReferringToNonExistingProduct,
        orderReferringToDelayedProduct,
        delayedProduct
      ),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToDelayedProduct.id,
        "product" -> Map(
          "id"   -> delayedProduct.id,
          "name" -> delayedProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should outer join stream") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(JoinType.OUTER),
      List(
        someProduct,
        anotherProduct,
        orderReferringToExistingProduct,
        orderReferringToNonExistingProduct,
        orderReferringToDelayedProduct,
        delayedProduct
      ),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToNonExistingProduct.id,
        "product" -> Map(
          "id"   -> null,
          "name" -> null
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToDelayedProduct.id,
        "product" -> Map(
          "id"   -> delayedProduct.id,
          "name" -> delayedProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should allow to use different types that can be compared in key expressions") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        orderKeyExpression = "#input.productId".spel,
        productKeyExpression = "#input.id.longValue".spel
      ),
      List(
        someProduct,
        orderReferringToExistingProduct
      ),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava
    )
  }

  test("shouldn't allow to use types that can't be compared in key expressions") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        orderKeyExpression = "#input.productId".spel,
        productKeyExpression = "#input.id.toString".spel
      ),
      List(orderReferringToNonExistingProduct),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.invalidValue.toList should matchPattern {
      case CustomNodeError(
            `joinNodeId`,
            "Types Integer and String are not comparable",
            Some(ParameterName("Key"))
          ) :: Nil =>
    }
  }

  test("should allow to use various types in main branch context") {
    val mainBranchPreJoinLocalVariableFields = Map(
      "nestedRecord"   -> "{foo: 1, bar: '123'}".spel,
      "listOfRecords"  -> "{{foo: 1, bar: '123'}}".spel,
      "instant"        -> SpelValues.spelInstant,
      "nullable"       -> "null".spel,
      "offsetDateTime" -> SpelValues.spelOffsetDateTime,
    )
    val postJoinLocalVariableFields = Map(
      "basedOnNullable" -> "#localPreJoin.nullable ? 1 : 0".spel,
//      "basedOnOffsetDateTime" ->
    )
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        mainBranchPreJoinLocalVariableFields = mainBranchPreJoinLocalVariableFields,
        postJoinLocalVariableFields = postJoinLocalVariableFields
      ),
      List(someProduct, orderReferringToExistingProduct),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should allow to use various types in keys") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        orderKeyExpression = "{foo: #input.productId}".spel,
        productKeyExpression = "{foo: #input.id}".spel
      ),
      List(someProduct, orderReferringToExistingProduct),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should allow to use various types in joined output") {
    val enrichedOrders = runner.runWithDataWithType[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        productOutputExpression = "{foo: #input}".spel,
        extractProductField = "#product?.foo." + _
      ),
      List(someProduct, orderReferringToExistingProduct),
      OrderOrProduct.`type`,
      Boundedness.BOUNDED
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava
    )
  }

  private def prepareJoiningScenario(
      joinType: JoinType,
      orderKeyExpression: Expression = "#input.productId".spel,
      productKeyExpression: Expression = "#input.id".spel,
      productOutputExpression: Expression = "#input".spel,
      extractProductField: String => String = "#product?." + _,
      mainBranchPreJoinLocalVariableFields: Iterable[(String, Expression)] = Seq.empty,
      postJoinLocalVariableFields: Iterable[(String, Expression)] = Seq.empty,
  ) = {
    ScenarioBuilder
      .streaming(classOf[TableJoinTest].getSimpleName)
      .sources(
        GraphBuilder
          .source("orders-source", TestScenarioRunner.testDataSource)
          .filter("orders-filter", "#input.type == 'order'".spel)
          .buildVariable(
            "example-pre-join-transformations",
            localPreJoinVariableName,
            mainBranchPreJoinLocalVariableFields.toSeq: _*
          )
          .branchEnd(mainBranchId, joinNodeId),
        GraphBuilder
          .source("products-source", TestScenarioRunner.testDataSource)
          .filter("product-filter", "#input.type == 'product'".spel)
          .branchEnd(joinedBranchId, joinNodeId),
        GraphBuilder
          .join(
            joinNodeId,
            "join",
            Some("product"),
            List(
              mainBranchId -> List(
                "Branch Type" -> s"T(${classOf[BranchType].getName}).${BranchType.MAIN}".spel,
                "Key"         -> orderKeyExpression
              ),
              joinedBranchId -> List(
                "Branch Type" -> s"T(${classOf[BranchType].getName}).${BranchType.JOINED}".spel,
                "Key"         -> productKeyExpression
              )
            ),
            "Join Type" -> s"T(${classOf[JoinType].getName}).$joinType".spel,
            "Output"    -> productOutputExpression,
          )
          .buildVariable("example-post-join-transformations", "localPostJoin", postJoinLocalVariableFields.toSeq: _*)
          .emptySink(
            "end",
            TestScenarioRunner.testResultSink,
            "value" ->
              s"""{
                 |  orderId: #input.id,
                 |  product: {
                 |   id: ${extractProductField("id")},
                 |   name: ${extractProductField("name")}
                 |  }
                 |}""".stripMargin.spel
          )
      )
  }

}

object TableJoinTest {

  type OrderOrProduct = Row

  object OrderOrProduct {

    val `type`: TypingResult = Typed.record(
      Map(
        // TODO: split into separate types and pass two streams to separate source nodes
        // productId is dedicated only for order events
        // name is dedicated only for product events
        "type"      -> Typed[String],
        "id"        -> Typed[Int],
        "name"      -> Typed[String],
        "productId" -> Typed[Int],
      ),
      Typed.typedClass[Row]
    )

    def createOrder(id: Int, productId: Int): Row = {
      val order = Row.withNames()
      order.setField("type", "order")
      order.setField("id", id)
      order.setField("productId", productId)
      order
    }

    def createProduct(id: Int, name: String): Row = {
      val product = Row.withNames()
      product.setField("type", "product")
      product.setField("id", id)
      product.setField("name", name)
      product
    }

    implicit class RowExtension(row: Row) {

      def id: Int = row.getFieldAs[Int]("id")

      def name: String = row.getFieldAs[String]("name")

    }

  }

}
