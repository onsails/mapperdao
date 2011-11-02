package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit
import com.googlecode.mapperdao.jdbc.Setup
import com.googlecode.mapperdao.exceptions.PersistException
import com.googlecode.mapperdao.jdbc.Queries

/**
 * this spec is self contained, all entities, mapping are contained in this class
 *
 * @author kostantinos.kougios
 *
 * 12 Jul 2011
 */
class OneToManyAutoGeneratedSpec extends SpecificationWithJUnit {

	import OneToManyAutoGeneratedSpec._
	val (jdbc, driver, mapperDao) = Setup.setupMapperDao(TypeRegistry(JobPositionEntity, HouseEntity, PersonEntity))

	"delete with DeleteConfig(true)" in {
		createTables(false)

		val inserted = mapperDao.insert(PersonEntity, Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(JobPosition("C++ Developer", 10), JobPosition("Scala Developer", 10))))

		mapperDao.delete(DeleteConfig(true), PersonEntity, inserted)

		jdbc.queryForInt("select count(*) from House") must_== 0
		jdbc.queryForInt("select count(*) from JobPosition") must_== 0
	}

	"delete with DeleteConfig(true,skip)" in {
		createTables(false)

		val inserted = mapperDao.insert(PersonEntity, Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(JobPosition("C++ Developer", 10), JobPosition("Scala Developer", 10))))

		mapperDao.delete(DeleteConfig(true, skip = Set(PersonEntity.jobPositions)), PersonEntity, inserted)
		jdbc.queryForInt("select count(*) from House") must_== 0
		jdbc.queryForInt("select count(*) from JobPosition") must_== 2
	}

	"select, skip one-to-many" in {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)

		// we need to apply the same sorting for JobPositions in order for the case classes to be equal because order of list items matters
		val person = Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2))
		val inserted = mapperDao.insert(PersonEntity, person)
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.owns, PersonEntity.jobPositions)), PersonEntity, List(inserted.id)).get must_== Person("Kostas", "K", Set(), 16, List())
		mapperDao.select(SelectConfig(skip = Set(PersonEntity.jobPositions)), PersonEntity, List(inserted.id)).get must_== Person("Kostas", "K", inserted.owns, 16, List())
	}

	"updating items (immutable)" in {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val jp4 = new JobPosition("Web Designer", 10)
		val jp5 = new JobPosition("Graphics Designer", 10)

		// we need to apply the same sorting for JobPositions in order for the case classes to be equal because order of list items matters
		val person = Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3).sortWith(_.name < _.name))
		val inserted = mapperDao.insert(PersonEntity, person)
		inserted must_== person
		var updated = inserted
		def doUpdate(from: Person with IntId, to: Person) =
			{
				updated = mapperDao.update(PersonEntity, from, to)
				val localUpdated = updated // for debugging
				updated must_== to
				val id = updated.id
				val loaded = mapperDao.select(PersonEntity, id).get
				loaded must_== updated
				loaded must_== to
			}
		doUpdate(updated, new Person("Changed", "K", updated.owns, 18, updated.positions.filterNot(_ == jp1)))
		doUpdate(updated, new Person("Changed Again", "Surname changed too", updated.owns.filter(_.address == "London"), 18, jp5 :: updated.positions.filterNot(jp => jp == jp1 || jp == jp3)))

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"updating items (mutable)" in {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions.foreach(_.name = "changed")
		inserted.positions.foreach(_.rank = 5)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated must_== inserted

		val loaded = mapperDao.select(PersonEntity, inserted.id).get
		loaded must_== updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"removing items" in {
		createTables(true)

		val jp1 = new JobPosition("C++ Developer", 10)
		val jp2 = new JobPosition("Scala Developer", 10)
		val jp3 = new JobPosition("Java Developer", 10)
		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(jp1, jp2, jp3))
		val inserted = mapperDao.insert(PersonEntity, person)

		inserted.positions = inserted.positions.filterNot(jp => jp == jp1 || jp == jp3)
		val updated = mapperDao.update(PersonEntity, inserted)
		updated must_== inserted

		val loaded = mapperDao.select(PersonEntity, inserted.id).get
		loaded must_== updated

		mapperDao.delete(PersonEntity, updated)
		mapperDao.select(PersonEntity, updated.id) must beNone
	}

	"adding items" in {
		createTables(true)

		val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(new JobPosition("Scala Developer", 10), new JobPosition("Java Developer", 10)))
		val inserted = mapperDao.insert(PersonEntity, person)
		val id = inserted.id

		val loaded = mapperDao.select(PersonEntity, id).get

		// add more elements to the collection
		loaded.positions = (new JobPosition("Groovy Developer", 5) :: new JobPosition("C++ Developer", 8) :: loaded.positions).sortWith(_.name < _.name)
		val updatedPositions = mapperDao.update(PersonEntity, loaded)
		updatedPositions must_== loaded

		val updatedReloaded = mapperDao.select(PersonEntity, id).get
		updatedReloaded must_== updatedPositions

		mapperDao.delete(PersonEntity, updatedReloaded)
		mapperDao.select(PersonEntity, updatedReloaded.id) must beNone
	}

	"using already persisted entities" in {
		createTables(true)
		Setup.database match {
			case "mysql" =>
				jdbc.update("alter table House modify person_id int")
			case "oracle" =>
				jdbc.update("alter table House modify (person_id null)")
			case "derby" =>
				jdbc.update("alter table House alter person_id null")
			case _ =>
				jdbc.update("alter table House alter person_id drop not null")
		}
		val h1 = mapperDao.insert(HouseEntity, House("London"))
		val h2 = mapperDao.insert(HouseEntity, House("Rhodes"))
		val h3 = mapperDao.insert(HouseEntity, House("Santorini"))

		val person = new Person("Kostas", "K", Set(h1, h2), 16, List(new JobPosition("Scala Developer", 10), new JobPosition("Java Developer", 10)))
		val inserted = mapperDao.insert(PersonEntity, person)
		val id = inserted.id

		mapperDao.select(PersonEntity, id).get must_== inserted

		val changed = Person("changed", "K", inserted.owns.filterNot(_ == h1), 18, List())
		val updated = mapperDao.update(PersonEntity, inserted, changed)
		updated must_== changed

		mapperDao.select(PersonEntity, id).get must_== updated
	}

	"CRUD (multi purpose test)" in {
		createTables(true)

		val items = for (i <- 1 to 10) yield {
			val person = new Person("Kostas", "K", Set(House("London"), House("Rhodes")), 16, List(new JobPosition("Java Developer", 10), new JobPosition("Scala Developer", 10)))
			val inserted = mapperDao.insert(PersonEntity, person)
			inserted must_== person

			val id = inserted.id

			val loaded = mapperDao.select(PersonEntity, id).get
			loaded must_== person

			// update

			loaded.name = "Changed"
			loaded.age = 24
			loaded.positions.head.name = "Java/Scala Developer"
			loaded.positions.head.rank = 123
			val updated = mapperDao.update(PersonEntity, loaded)
			updated must_== loaded

			val reloaded = mapperDao.select(PersonEntity, id).get
			reloaded must_== loaded

			// add more elements to the collection
			reloaded.positions = new JobPosition("C++ Developer", 8) :: reloaded.positions
			val updatedPositions = mapperDao.update(PersonEntity, reloaded)
			updatedPositions must_== reloaded

			val updatedReloaded = mapperDao.select(PersonEntity, id).get
			updatedReloaded must_== updatedPositions

			// remove elements from the collection
			updatedReloaded.positions = updatedReloaded.positions.filterNot(_ == updatedReloaded.positions(1))
			val removed = mapperDao.update(PersonEntity, updatedReloaded)
			removed must_== updatedReloaded

			val removedReloaded = mapperDao.select(PersonEntity, id).get
			removedReloaded must_== removed

			// remove them all
			removedReloaded.positions = List()
			mapperDao.update(PersonEntity, removedReloaded) must_== removedReloaded

			val rereloaded = mapperDao.select(PersonEntity, id).get
			rereloaded must_== removedReloaded
			rereloaded.positions = List(JobPosition("Java Developer %d".format(i), 15 + i))
			rereloaded.name = "final name %d".format(i)
			val reupdated = mapperDao.update(PersonEntity, rereloaded)

			val finalLoaded = mapperDao.select(PersonEntity, id).get
			finalLoaded must_== reupdated
			(id, finalLoaded)
		}
		val ids = items.map(_._1)
		ids.toSet.size must_== ids.size
		// verify all still available
		items.foreach { item =>
			mapperDao.select(PersonEntity, item._1).get must_== item._2
		}
		success
	}

	def createTables(cascade: Boolean) {
		Setup.dropAllTables(jdbc)
		Setup.queries(this, jdbc).update(if (cascade) "cascade" else "nocascade")

		Setup.database match {
			case "postgresql" =>
				Setup.createSeq(jdbc, "PersonSeq")
				Setup.createSeq(jdbc, "JobPositionSeq")
				Setup.createSeq(jdbc, "HouseSeq")
			case "oracle" =>
				Setup.createSeq(jdbc, "PersonSeq")
				Setup.createSeq(jdbc, "JobPositionSeq")
				Setup.createSeq(jdbc, "HouseSeq")
			case _ =>
		}
	}
}

object OneToManyAutoGeneratedSpec {
	/**
	 * ============================================================================================================
	 * the entities
	 * ============================================================================================================
	 */
	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention.
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class JobPosition(var name: String, var rank: Int) {
		// this can have any arbitrary methods, no problem!
		def whatRank = rank
		// also any non persisted fields, no prob! It's up to the mappings regarding which fields will be used
		val whatever = 5
	}

	/**
	 * the only reason this is a case class, is to ease testing. There is no requirement
	 * for persisted classes to follow any convention
	 *
	 * Also the only reason for this class to be mutable is for testing. In a real application
	 * it could be immutable.
	 */
	case class Person(var name: String, val surname: String, owns: Set[House], var age: Int, var positions: List[JobPosition]) {
	}

	case class House(val address: String)

	/**
	 * ============================================================================================================
	 * Mapping for JobPosition class
	 * ============================================================================================================
	 */

	object JobPositionEntity extends Entity[IntId, JobPosition](classOf[JobPosition]) {
		// this is the primary key
		val id = Setup.database match {
			case "postgresql" | "oracle" =>
				intAutoGeneratedPK("id", "JobPositionSeq", _.id)
			case _ => intAutoGeneratedPK("id", _.id)
		}
		val name = string("name", _.name) // _.name : JobPosition => Any . Function that maps the column to the value of the object
		val rank = int("rank", _.rank)

		def constructor(implicit m: ValuesMap) = new JobPosition(name, rank) with Persisted with IntId {
			val id: Int = JobPositionEntity.id
		}
	}

	object HouseEntity extends Entity[IntId, House](classOf[House]) {
		val id = Setup.database match {
			case "postgresql" | "oracle" =>
				intAutoGeneratedPK("id", "HouseSeq", _.id)
			case _ => intAutoGeneratedPK("id", _.id)
		}
		val address = string("address", _.address)

		def constructor(implicit m: ValuesMap) = new House(address) with Persisted with IntId {
			val id: Int = HouseEntity.id
		}
	}

	object PersonEntity extends Entity[IntId, Person](classOf[Person]) {
		val id = Setup.database match {
			case "postgresql" | "oracle" =>
				intAutoGeneratedPK("id", "PersonSeq", _.id)
			case _ => intAutoGeneratedPK("id", _.id)
		}
		val name = string("name", _.name)
		val surname = string("surname", _.surname)
		val owns = oneToMany(HouseEntity, _.owns)
		val age = int("age", _.age)
		/**
		 * a traversable one-to-many relationship with JobPositions.
		 * The type of the relationship is classOf[JobPosition] and the alias
		 * for retrieving the Traversable is jobPositionsAlias. This is used above, when
		 * creating Person: new Person(....,m.toList("jobPositionsAlias")) .
		 * JobPositions table has a person_id foreign key which references Person table.
		 */
		val jobPositions = oneToMany(JobPositionEntity, _.positions)

		def constructor(implicit m: ValuesMap) = new Person(name, surname, owns, age, m(jobPositions).toList.sortWith(_.name < _.name)) with Persisted with IntId {
			val id: Int = PersonEntity.id
		}
	}
}