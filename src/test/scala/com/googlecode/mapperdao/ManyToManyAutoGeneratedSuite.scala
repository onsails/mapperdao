package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import com.googlecode.mapperdao.utils.Helpers

/**
 * @author kostantinos.kougios
 *
 *         6 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class ManyToManyAutoGeneratedSuite extends FunSuite with ShouldMatchers {

	import ManyToManyAutoGeneratedSuite._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(TypeRegistry(ProductEntity, AttributeEntity))

	test("multiple entities, all new") {
		createTables(true)
		val p1 = Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set())))
		val p2 = Product("green jean", Set(Attribute("colour", "green", Set()), Attribute("size", "small", Set())))

		val inserted = mapperDao.insertBatch(UpdateConfig.default, ProductEntity, p1 :: p2 :: Nil)

		inserted should be(p1 :: p2 :: Nil)

		import Query._
		(select from ProductEntity orderBy (ProductEntity.id)).toList(queryDao) should be(p1 :: p2 :: Nil)

		val a1 = inserted.head.attributes.head
		val a2 = inserted.head.attributes.tail.head
		val p1u = p1.copy(name = "b jeans", attributes = Set(a1))
		val p2u = p2.copy(name = "g jeans", attributes = Set(a2))
		val updated = mapperDao.updateBatch(UpdateConfig.default, ProductEntity, (inserted.head, p1u) ::(inserted.tail.head, p2u) :: Nil)
		updated should be(p1u :: p2u :: Nil)
		(select from ProductEntity orderBy (ProductEntity.id)).toList(queryDao) should be(p1u :: p2u :: Nil)
	}

	test("multiple entities, with existing") {
		createTables(true)

		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "blue", Set()))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))

		val p1 = Product("blue jean", Set(a1, a2))
		val p2 = Product("green jean", Set(a1, a2, Attribute("colour", "green", Set()), Attribute("size", "small", Set())))

		val inserted = mapperDao.insertBatch(UpdateConfig.default, ProductEntity, p1 :: p2 :: Nil)

		inserted should be(p1 :: p2 :: Nil)

		import Query._
		(select from ProductEntity orderBy (ProductEntity.id)).toList(queryDao) should be(p1 :: p2 :: Nil)

		val p1u = p1.copy(name = "b jeans", attributes = Set(a1))
		val p2u = p2.copy(name = "g jeans", attributes = Set(a2))
		val updated = mapperDao.updateBatch(UpdateConfig.default, ProductEntity, (inserted.head, p1u) ::(inserted.tail.head, p2u) :: Nil)
		updated should be(p1u :: p2u :: Nil)
		(select from ProductEntity orderBy (ProductEntity.id)).toList(queryDao) should be(p1u :: p2u :: Nil)
	}

	test("insert tree of entities with cyclic references") {
		createTables(true)
		val product = Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set())))
		val inserted = mapperDao.insert(ProductEntity, product)
		test(product, inserted)

		// due to cyclic reference, the attributes set contains "mock" products.
		val selected = mapperDao.select(ProductEntity, inserted.id).get
		test(inserted, selected)

		// attributes->product should also work
		val colour = inserted.attributes.toList.filter(_.name == "colour").head
		val loadedAttribute = mapperDao.select(AttributeEntity, Helpers.intIdOf(colour)).get

		loadedAttribute should be === colour
		loadedAttribute.products should be === Set(Product("blue jean", Set()))

		mapperDao.delete(ProductEntity, inserted)
		mapperDao.select(ProductEntity, inserted.id) should be(None)
	}

	test("insert aquires id's") {
		createTables(false)
		val inserted = mapperDao.insert(ProductEntity, Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set()))))
		inserted.id should be === 1
		val a1 = inserted.attributes.head
		Helpers.intIdOf(a1) should be > 0

		val selected = mapperDao.select(ProductEntity, inserted.id).get
		selected.id should be === 1
		val a2 = selected.attributes.head
		Helpers.intIdOf(a2) should be > 0
	}

	test("delete with DeleteConfig(true)") {
		createTables(false)
		val inserted = mapperDao.insert(ProductEntity, Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set()))))
		mapperDao.delete(DeleteConfig(true), ProductEntity, inserted)
		jdbc.queryForInt("select count(*) from Attribute") should be === 2
		jdbc.queryForInt("select count(*) from Product_Attribute") should be === 0
	}

	test("delete with DeleteConfig(true,skip)") {
		createTables(false)
		val inserted = mapperDao.insert(ProductEntity, Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set()))))
		mapperDao.delete(DeleteConfig(true, skip = Set(ProductEntity.attributes)), ProductEntity, inserted)
		jdbc.queryForInt("select count(*) from Attribute") should be === 2
		jdbc.queryForInt("select count(*) from Product_Attribute") should be === 2
	}

	test("select with skip") {
		createTables(true)
		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "blue", Set()))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))
		val product = Product("blue jean", Set(a1, a2))
		val inserted = mapperDao.insert(ProductEntity, product)

		val selected = mapperDao.select(SelectConfig(skip = Set(ProductEntity.attributes)), ProductEntity, inserted.id).get
		selected.attributes should be === Set()
	}

	test("randomize id's, update and select") {
		createTables(true)
		val l = for (i <- 1 to 5) yield {
			val product = Product("blue jean" + i, Set(Attribute("colour" + i, "blue" + i, Set()), Attribute("size" + i * 2, "medium" + i * 2, Set())))
			val inserted = mapperDao.insert(ProductEntity, product)
			inserted
		}
		val updated = mapperDao.update(ProductEntity, l.last, Product("blue jeanX", l.last.attributes.filterNot(_.name == "colour")))
		mapperDao.select(ProductEntity, l.last.id).get should be === updated
		mapperDao.select(ProductEntity, l.head.id).get should be === l.head
	}

	test("update leaf entity relationship") {
		createTables(true)
		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "blue", Set()))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))
		val product = Product("blue jean", Set(a1, a2))
		val inserted = mapperDao.insert(ProductEntity, product)

		val sa1 = mapperDao.select(AttributeEntity, a1.id).get
		val ua1 = mapperDao.update(AttributeEntity, sa1, Attribute("colour", "blue", Set()))
		mapperDao.select(ProductEntity, inserted.id).get should be === Product("blue jean", Set(a2))
		mapperDao.select(AttributeEntity, ua1.id).get should be === Attribute("colour", "blue", Set())

		val ua2 = mapperDao.update(AttributeEntity, ua1, Attribute("colour", "blue", Set(inserted)))
		mapperDao.select(ProductEntity, inserted.id).get should be === Product("blue jean", Set(ua2, a2))
		mapperDao.select(AttributeEntity, ua2.id).get should be === Attribute("colour", "blue", Set(inserted))
	}

	test("update leaf entity values") {
		createTables(true)
		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "blue", Set()))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))
		val product = Product("blue jean", Set(a1, a2))
		val inserted = mapperDao.insert(ProductEntity, product)

		val sa1 = mapperDao.select(AttributeEntity, a1.id).get
		val ua1 = mapperDao.update(AttributeEntity, sa1, Attribute("colour", "red", sa1.products))
		mapperDao.select(ProductEntity, inserted.id).get should be === Product("blue jean", Set(ua1, a2))
		mapperDao.select(AttributeEntity, ua1.id).get should be === Attribute("colour", "red", sa1.products)
	}

	test("randomize id's and select") {
		createTables(true)
		val l = for (i <- 1 to 5) yield {
			val product = Product("blue jean" + i, Set(Attribute("colour" + i, "blue" + i, Set()), Attribute("size" + i * 2, "medium" + i * 2, Set())))
			mapperDao.insert(ProductEntity, product)
		}
		mapperDao.select(ProductEntity, l.last.id).get should be === l.last
		mapperDao.select(ProductEntity, l.head.id).get should be === l.head
	}

	test("insert tree of entities (cyclic references)  leaf entities") {
		createTables(true)
		val a1 = mapperDao.insert(AttributeEntity, Attribute("colour", "blue", Set()))
		val a2 = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))
		val product = Product("blue jean", Set(a1, a2))
		val inserted = mapperDao.insert(ProductEntity, product)
		test(product, inserted)

		// due to cyclic reference, the attributes collection contains "mock" products
		val selected = mapperDao.select(ProductEntity, inserted.id).get
		test(inserted, selected)

		mapperDao.delete(ProductEntity, inserted)
		mapperDao.select(ProductEntity, inserted.id) should be(None)
	}

	test("update tree of entities, remove entity from set") {
		createTables(true)
		val product = Product("blue jean", Set(Attribute("colour", "blue", Set()), Attribute("size", "medium", Set()), Attribute("size", "large", Set())))
		val inserted = mapperDao.insert(ProductEntity, product)

		inserted.attributes.foreach {
			case p: Persisted =>
				p.mapperDaoValuesMap.identity should be(System.identityHashCode(p))
		}

		val changed = Product("just jean", inserted.attributes.filterNot(_.name == "size"));
		val updated = mapperDao.update(ProductEntity, inserted, changed)
		test(changed, updated)

		val selected = mapperDao.select(ProductEntity, updated.id).get
		test(updated, selected)

		mapperDao.delete(ProductEntity, updated)
		mapperDao.select(ProductEntity, updated.id) should be(None)
	}

	test("update tree of entities, add new entities to set") {
		createTables(true)
		val product = Product("blue jean", Set(Attribute("colour", "blue", Set())))
		val inserted = mapperDao.insert(ProductEntity, product)

		val changed = Product("just jean", inserted.attributes + Attribute("size", "medium", Set()) + Attribute("size", "large", Set()));
		val updated = mapperDao.update(ProductEntity, inserted, changed)
		test(changed, updated)

		val selected = mapperDao.select(ProductEntity, updated.id).get
		test(updated, selected)

		mapperDao.delete(ProductEntity, updated)
		mapperDao.select(ProductEntity, updated.id) should be(None)
	}

	test("update tree of entities, add persisted entity to set") {
		createTables(true)
		val product = Product("blue jean", Set(Attribute("colour", "blue", Set())))
		val inserted = mapperDao.insert(ProductEntity, product)

		val persistedA = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))

		val changed = Product("just jean", inserted.attributes + persistedA + Attribute("size", "large", Set()));
		val updated = mapperDao.update(ProductEntity, inserted, changed)
		test(changed, updated)

		val selected = mapperDao.select(ProductEntity, Helpers.intIdOf(updated)).get
		test(updated, selected)

		mapperDao.delete(ProductEntity, updated)
		mapperDao.select(ProductEntity, updated.id) should be(None)
	}

	test("update tree of entities, add updated entity to set") {
		createTables(true)
		val product = Product("blue jean", Set(Attribute("colour", "blue", Set())))
		val inserted = mapperDao.insert(ProductEntity, product)

		val persistedA = mapperDao.insert(AttributeEntity, Attribute("size", "medium", Set()))
		val updateOfPersistedA = mapperDao.update(AttributeEntity, persistedA, Attribute("size", "mediumX", Set()))

		val changed = Product("just jean", inserted.attributes + updateOfPersistedA + Attribute("size", "large", Set()));
		val updated = mapperDao.update(ProductEntity, inserted, changed)
		test(changed, updated)
		updated.attributes.contains(Attribute("size", "mediumX", Set())) should be(true)

		val selected = mapperDao.select(ProductEntity, Helpers.intIdOf(updated)).get
		test(updated, selected)

		mapperDao.delete(ProductEntity, updated)
		mapperDao.select(ProductEntity, updated.id) should be(None)
	}

	test("combination of ops") {
		createTables(true)
		val p1 = Product("t-shirt", Set())
		val p2 = Product("a dress", Set(Attribute("size", "12", Set())))
		val a1 = Attribute("colour", "blue", Set())
		val a2 = Attribute("colour", "red", Set(p1, p2))
		val product = Product("blue jean", Set(a1, a2))
		val inserted = mapperDao.insert(ProductEntity, product)
		test(product, inserted)
		test(p1, inserted.attributes.find(_.value == "red").get.products.find(_ == p1).get)
		test(p2, inserted.attributes.find(_.value == "red").get.products.find(_ == p2).get)

		test(inserted, mapperDao.select(ProductEntity, inserted.id).get)
		//a2 was associated not only with p1,p2 but also with product 
		test(Attribute("colour", "red", Set(p1, p2, product)), mapperDao.select(AttributeEntity, Helpers.intIdOf(inserted.attributes.find(_.value == "red").get)).get)

		mapperDao.delete(ProductEntity, inserted)
		mapperDao.select(ProductEntity, inserted.id) should be(None)
	}

	test("batch crud") {
		createTables(true)

		// noise
		mapperDao.insertBatch(AttributeEntity, Attribute("x1", "a1", Set()) :: Attribute("x2", "a2", Set()) :: Nil)

		val a1 = Attribute("colour", "blue", Set())
		val a2 = Attribute("colour", "red", Set())
		val a3 = Attribute("colour", "green", Set())
		val a4 = Attribute("colour", "yellow", Set())
		val List(a1i, a2i, a3i, a4i) = mapperDao.insertBatch(AttributeEntity, a1 :: a2 :: a3 :: a4 :: Nil)
		a1i should be(a1)
		a2i should be(a2)
		a3i should be(a3)
		a4i should be(a4)

		mapperDao.select(AttributeEntity, a1i.id).get should be(a1i)
		mapperDao.select(AttributeEntity, a2i.id).get should be(a2i)
		mapperDao.select(AttributeEntity, a3i.id).get should be(a3i)
		mapperDao.select(AttributeEntity, a4i.id).get should be(a4i)

		val p1 = Product("p1", Set(a1i, a2i))
		val p2 = Product("p2", Set(a2i, a3i))

		val List(p1i, p2i) = mapperDao.insertBatch(ProductEntity, p1 :: p2 :: Nil)
		test(p1, p1i)
		test(p2, p2i)

		val loaded1 = mapperDao.select(ProductEntity, p1i.id).get
		test(p1i, loaded1)
		val loaded2 = mapperDao.select(ProductEntity, p2i.id).get
		test(p2i, loaded2)

		val up1 = loaded1.copy(attributes = loaded1.attributes - a1i + a3i)
		val up2 = loaded1.copy(attributes = loaded2.attributes - a2i + a4i)
		val List(u1, u2) = mapperDao.updateBatch(ProductEntity, List((loaded1, up1), (loaded2, up2)))
		test(up1, u1)
		test(up2, u2)

		val reloaded1 = mapperDao.select(ProductEntity, u1.id).get
		test(u1, reloaded1)
		val reloaded2 = mapperDao.select(ProductEntity, u2.id).get
		test(u2, reloaded2)
	}

	test("batch insert, shared instances") {
		createTables(true)
		// noise
		mapperDao.insertBatch(AttributeEntity, Attribute("x1", "a1", Set()) :: Attribute("x2", "a2", Set()) :: Nil)

		val a1 = Attribute("colour", "blue", Set())
		val a2 = Attribute("colour", "red", Set())
		val a3 = Attribute("colour", "green", Set())

		val p1 = Product("p1", Set(a1, a2))
		val p2 = Product("p2", Set(a2, a3))

		val List(p1i, p2i) = mapperDao.insertBatch(ProductEntity, p1 :: p2 :: Nil)
		test(p1, p1i)
		test(p2, p2i)
	}

	test("batch update, shared instances, update on inserted objects") {
		createTables(true)
		// noise
		mapperDao.insertBatch(AttributeEntity, Attribute("x1", "a1", Set()) :: Attribute("x2", "a2", Set()) :: Nil)

		val a1 = Attribute("colour", "blue", Set())
		val a2 = Attribute("colour", "red", Set())
		val a3 = Attribute("colour", "green", Set())
		val a4 = Attribute("colour", "yellow", Set())

		val p1 = Product("p1", Set(a1, a2))
		val p2 = Product("p2", Set(a2, a3))

		val List(p1i, p2i) = mapperDao.insertBatch(ProductEntity, p1 :: p2 :: Nil)
		val p1u = p1i.copy(attributes = p1i.attributes + a4)
		val p2u = p2i.copy(attributes = p2i.attributes + a4)
		val List(u1, u2) = mapperDao.updateBatch(ProductEntity, List(
			(p1i, p1u),
			(p2i, p2u)
		))
		test(p1u, u1)
		test(p2u, u2)

		val reloaded1 = mapperDao.select(ProductEntity, u1.id).get
		test(u1, reloaded1)
		val reloaded2 = mapperDao.select(ProductEntity, u2.id).get
		test(u2, reloaded2)
	}

	test("batch update, shared instances, update on selected objects") {
		createTables(true)
		// noise
		mapperDao.insertBatch(AttributeEntity, Attribute("x1", "a1", Set()) :: Attribute("x2", "a2", Set()) :: Nil)

		val a1 = Attribute("colour", "blue", Set())
		val a2 = Attribute("colour", "red", Set())
		val a3 = Attribute("colour", "green", Set())
		val a4 = Attribute("colour", "yellow", Set())

		val p1 = Product("p1", Set(a1, a2))
		val p2 = Product("p2", Set(a2, a3))

		val List(p1i, p2i) = mapperDao.insertBatch(ProductEntity, p1 :: p2 :: Nil).map {
			p =>
				mapperDao.select(ProductEntity, p.id).get
		}

		val p1u = p1i.copy(attributes = p1i.attributes + a4)
		val p2u = p2i.copy(attributes = p2i.attributes + a4)
		val List(u1, u2) = mapperDao.updateBatch(ProductEntity, List(
			(p1i, p1u),
			(p2i, p2u)
		))
		test(p1u, u1)
		test(p2u, u2)

		val reloaded1 = mapperDao.select(ProductEntity, u1.id).get
		test(u1, reloaded1)
		val reloaded2 = mapperDao.select(ProductEntity, u2.id).get
		test(u2, reloaded2)
	}

	def test(expected: Product, actual: Product) = {
		actual.attributes should be === expected.attributes
		actual should be === expected

		if (!actual.attributes.isEmpty)
			actual.attributes.eq(expected.attributes) should be(false)
	}

	def test(expected: Attribute, actual: Attribute) = {
		actual.products should be === expected.products
		actual should be === expected
		actual.products.eq(expected.products) should be(false)
	}

	def createTables(cascadeOnDelete: Boolean) = {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("cascade-%s".format(cascadeOnDelete match {
			case true => "on"
			case false => "off"
		}))

		Setup.database match {
			case "oracle" =>
				Setup.createSeq(jdbc, "ProductSeq")
				Setup.createSeq(jdbc, "AttributeSeq")
			case _ =>
		}
	}
}

object ManyToManyAutoGeneratedSuite {

	case class Product(val name: String, val attributes: Set[Attribute]) {
		override def equals(o: Any): Boolean = o match {
			case p: Product => name == p.name
			case _ => false
		}

		override def toString: String = "Product(%s)".format(name)
	}

	case class Attribute(val name: String, val value: String, val products: Set[Product]) {
		/**
		 * since there are cyclic references, we override equals
		 * to avoid StackOverflowError
		 */
		override def equals(o: Any): Boolean = o match {
			case a: Attribute => a.name == name && a.value == value
			case _ => false
		}

		override def toString: String = "Attribute(%s,%s)".format(name, value)
	}

	def useSequences(seq: String) = Setup.database match {
		case "oracle" => Some(seq)
		case _ => None
	}

	object ProductEntity extends Entity[Int,SurrogateIntId, Product] {
		val id = key("id") sequence useSequences("ProductSeq") autogenerated (_.id)
		val name = column("name") to (_.name)
		val attributes = manytomany(AttributeEntity) to (_.attributes)

		def constructor(implicit m) = new Product(name, attributes) with Stored {
			val id: Int = ProductEntity.id
		}
	}

	object AttributeEntity extends Entity[Int,SurrogateIntId, Attribute] {
		val id = Setup.database match {
			case "oracle" => key("id") sequence useSequences("AttributeSeq") autogenerated (_.id)
			case _ => key("id") autogenerated (_.id)
		}
		val name = column("name") to (_.name)
		val value = column("value") to (_.value)
		val products = manytomanyreverse(ProductEntity) to (_.products)

		def constructor(implicit m) = new Attribute(name, value, products) with Stored {
			val id: Int = AttributeEntity.id
		}
	}

}