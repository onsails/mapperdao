package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * @author kostantinos.kougios
 *
 *         8 Nov 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToManySimpleTypesSuite extends FunSuite with ShouldMatchers {

	import ManyToManySimpleTypesSuite._

	val typeRegistry = TypeRegistry(ProductEntity)
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(typeRegistry)

	test("insert, string based") {
		createTables("string-based")
		val product = Product("computer", Set("PC", "laptop"))
		val inserted = mapperDao.insert(ProductEntity, product)
		inserted should be === product
	}

	test("select, string based") {
		createTables("string-based")
		val inserted = mapperDao.insert(ProductEntity, Product("computer", Set("PC", "laptop")))
		mapperDao.select(ProductEntity, inserted.id).get should be === inserted
	}

	test("update, string based") {
		createTables("string-based")
		val inserted = mapperDao.insert(ProductEntity, Product("computer", Set("PC", "laptop")))
		val updated = mapperDao.update(ProductEntity, inserted, Product("computer", Set("PC")))
		updated should be === Product("computer", Set("PC"))
		mapperDao.select(ProductEntity, inserted.id).get should be === updated
	}

	def createTables(sql: String) {
		Setup.dropAllTables(jdbc)
		val queries = Setup.queries(this, jdbc)
		queries.update(sql)
		Setup.database match {
			case "oracle" =>
				Setup.createSeq(jdbc, "ProductSeq")
				Setup.createSeq(jdbc, "CategorySeq")
			case _ =>
		}
	}
}

object ManyToManySimpleTypesSuite {

	case class Product(val name: String, val categories: Set[String])

	val se = StringEntity.manyToManyAutoGeneratedPK("Category", "id", "name", Setup.database match {
		case "oracle" => Some("CategorySeq")
		case _ => None
	})

	object ProductEntity extends Entity[Int,SurrogateIntId, Product] {
		val id = key("id") sequence (Setup.database match {
			case "oracle" => Some("ProductSeq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val categories = manytomany(se) join("Product_Category", "product_id", "category_id") tostring (_.categories)

		def constructor(implicit m) = new Product(name, categories) with Stored {
			val id: Int = ProductEntity.id
		}
	}

}