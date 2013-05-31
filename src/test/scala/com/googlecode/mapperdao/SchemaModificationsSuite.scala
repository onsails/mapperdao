package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.googlecode.mapperdao.schema.SchemaModifications

/**
 * @author kkougios
 */
@RunWith(classOf[JUnitRunner])
class SchemaModificationsSuite extends FunSuite with ShouldMatchers
{
	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(PersonEntity :: CommonEntities.AllEntities)

	if (Setup.database == "h2") {

		val modifications = SchemaModifications(
			name => "tmp_" + name
		)
		val sc = SelectConfig.default.copy(schemaModifications = modifications)
		val dc = DeleteConfig.default.copy(schemaModifications = modifications)
		val qc = QueryConfig.default.copy(schemaModifications = modifications)
		val uc = UpdateConfig.default.copy(schemaModifications = modifications, deleteConfig = dc)

		test("table name modified CRUD") {
			createTables()

			val List(i1, _) = mapperDao.insertBatch(uc, PersonEntity, List(Person("p1"), Person("p2")))

			val s1 = mapperDao.select(sc, PersonEntity, i1.id).get
			s1 should be(i1)

			val u1 = mapperDao.update(uc, PersonEntity, s1, Person("p1updated"))

			val s1u = mapperDao.select(sc, PersonEntity, i1.id).get
			s1u should be(u1)

			mapperDao.delete(dc, PersonEntity, s1u)

			mapperDao.select(sc, PersonEntity, i1.id) should be(None)
		}

		test("table name modified Query") {
			createTables()

			val List(i1, i2) = mapperDao.insertBatch(uc, PersonEntity, List(Person("p1"), Person("p2")))

			import Query._
			val r = queryDao.query(qc, select from PersonEntity).toSet

			r should be(Set(i1, i2))
		}

		test("CRUD, many to many") {
			createTablesCommon()
			import CommonEntities._

			val a1 = Attribute("a1", "v1")
			val a2 = Attribute("a2", "v2")
			val a3 = Attribute("a3", "v3")

			val p1 = Product("p1", Set(a1, a2))
			val p2 = Product("p2", Set(a1, a3))
			val List(i1, i2) = mapperDao.insertBatch(uc, ProductEntity, List(p1, p2))

			val s1 = mapperDao.select(sc, ProductEntity, i1.id).get
			s1 should be(p1)

			val u1 = mapperDao.update(uc, ProductEntity, s1, Product("p1updated", s1.attributes - a2 + Attribute("a4", "v4")))
			mapperDao.select(sc, ProductEntity, i1.id).get should be(u1)

			mapperDao.delete(dc, ProductEntity, u1)

			mapperDao.select(sc, ProductEntity, i1.id) should be(None)

			mapperDao.select(sc, ProductEntity, i2.id).get should be(i2)
		}

		test("query, many to many") {
			createTablesCommon()
			import CommonEntities._

			val a1 = Attribute("a1", "v1")
			val a2 = Attribute("a2", "v2")
			val a3 = Attribute("a3", "v3")

			val p1 = Product("p1", Set(a1, a2))
			val p2 = Product("p2", Set(a1, a3))
			val List(i1, i2) = mapperDao.insertBatch(uc, ProductEntity, List(p1, p2))

			import Query._
			queryDao.query(qc, select from ProductEntity).toSet should be(Set(i1, i2))

		}

		def createTables() {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("person")
		}

		def createTablesCommon() {
			Setup.dropAllTables(jdbc)
			Setup.queries(this, jdbc).update("product-attribute")
		}
	}

	case class Person(name: String)

	object PersonEntity extends Entity[Int, SurrogateIntId, Person]
	{
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)

		def constructor(implicit m: ValuesMap) = new Person(name) with Stored
		{
			val id: Int = PersonEntity.id
		}
	}

}
