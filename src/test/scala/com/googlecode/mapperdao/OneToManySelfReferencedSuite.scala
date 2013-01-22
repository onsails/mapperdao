package com.googlecode.mapperdao

import com.googlecode.mapperdao.jdbc.Setup
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

/**
 * tests one-to-many references to self
 *
 * @author kostantinos.kougios
 *
 *         5 Aug 2011
 */
@RunWith(classOf[JUnitRunner])
class OneToManySelfReferencedSuite extends FunSuite with ShouldMatchers {

	import OneToManySelfReferencedSuite._

	val (jdbc, mapperDao, queryDao) = setup

	test("insert") {
		createTables

		val person = new Person("main-person", Set(new Person("friend1", Set()), new Person("friend2", Set())))
		val inserted = mapperDao.insert(PersonEntity, person)
		inserted should be === person
	}

	test("insert and select") {
		createTables

		val person = new Person("main-person", Set(new Person("friend1", Set()), new Person("friend2", Set())))
		val inserted = mapperDao.insert(PersonEntity, person)
		val selected = mapperDao.select(PersonEntity, inserted.id).get
		selected should be(person)
	}

	test("update, remove from traversable") {
		createTables

		val person = new Person("main-person", Set(new Person("friend1", Set()), new Person("friend2", Set())))
		val inserted = mapperDao.insert(PersonEntity, person)

		val modified = new Person("main-changed", inserted.friends.filterNot(_.name == "friend1"))
		val updated = mapperDao.update(PersonEntity, inserted, modified)
		updated should be === modified

		mapperDao.select(PersonEntity, updated.id).get should be === updated
	}

	test("update, add to traversable") {
		createTables

		val person = new Person("main-person", Set(new Person("friend1", Set()), new Person("friend2", Set())))
		val inserted = mapperDao.insert(PersonEntity, person)
		val friends = inserted.friends
		val modified = new Person("main-changed", friends + new Person("friend3", Set()))
		val updated = mapperDao.update(PersonEntity, inserted, modified)
		updated should be === modified

		mapperDao.select(PersonEntity, updated.id).get should be === updated
	}

	test("3 levels deep") {
		createTables

		val person = Person("level1", Set(Person("level2-friend1", Set(Person("level3-friend1-1", Set()), Person("level3-friend1-2", Set()))), Person("level2-friend2", Set(Person("level3-friend2-1", Set())))))
		val inserted = mapperDao.insert(PersonEntity, person)

		val modified = Person("main-changed", inserted.friends + Person("friend3", Set(Person("level3-friend3-1", Set()))))
		val updated = mapperDao.update(PersonEntity, inserted, modified)
		updated should be === modified

		mapperDao.select(PersonEntity, updated.id).get should be === updated
	}

	test("use already persisted friends") {
		val level3 = Set(Person("level3-friend1-1", Set()), Person("level3-friend1-2", Set()))
		val level3Inserted = level3.map(mapperDao.insert(PersonEntity, _)).toSet[Person]
		val person = Person("level1", Set(Person("level2-friend1", level3Inserted), Person("level2-friend2", Set(Person("level3-friend2-1", Set())))))
		val inserted = mapperDao.insert(PersonEntity, person)

		val modified = Person("main-changed", inserted.friends + Person("friend3", Set(Person("level3-friend3-1", Set()))))
		val updated = mapperDao.update(PersonEntity, inserted, modified)
		updated should be === modified

		mapperDao.select(PersonEntity, updated.id).get should be === updated
	}

	def setup = {

		val typeRegistry = TypeRegistry(PersonEntity)

		Setup.setupMapperDao(typeRegistry)
	}

	def createTables {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update("ddl")
		Setup.database match {
			case "oracle" =>
				Setup.createMySeq(jdbc)
			case _ =>
		}
	}
}

object OneToManySelfReferencedSuite {

	case class Person(val name: String, val friends: Set[Person])

	object PersonEntity extends Entity[Int, Person] {
		type Stored = SurrogateIntId
		val aid = key("id") sequence (Setup.database match {
			case "oracle" => Some("myseq")
			case _ => None
		}) autogenerated (_.id)
		val name = column("name") to (_.name)
		val friends = onetomany(PersonEntity) foreignkey "friend_id" to (_.friends)

		def constructor(implicit m) = new Person(name, friends) with Stored {
			val id: Int = aid
		}
	}

}