package pl.touk.nussknacker.ui.api.helpers

//Representation of categories from ui.conf
object TestCategories {
  val Category1 = "Category1"
  val Category2 = "Category2"
  val TESTCAT = "TESTCAT"
  val TESTCAT2 = "TESTCAT2"
  val ReqRes = "ReqRes"

  val catCategories = List(Category1, Category2)
  val testCategories = List(TESTCAT, TESTCAT2)
  val reqResCategories = List(ReqRes)

  val allCategories: List[String] = catCategories ++ testCategories ++ reqResCategories
}
