package com.googlecode.mapperdao
import org.specs2.mutable.SpecificationWithJUnit
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * Option integration
 *
 * @author kostantinos.kougios
 *
 * 30 Oct 2011
 */
@RunWith(classOf[JUnitRunner])
class OptionSpec extends SpecificationWithJUnit {
	case class Category(val name: String, val parent: Option[Category], val linked: Option[Category])
	case class Dog(val name: Option[String])

	object CategoryEntity extends Entity[IntId, Category](classOf[Category]) {
		val id = key("id") autogenerated (_.id)
		val name = column("name") to (_.name)
		// self reference 
		val parent = manyToOneOption("parent_id", this, _.parent)
		val linked = oneToOneOption(this, _.linked)

		def constructor(implicit m: ValuesMap) =
			new Category(name, parent, linked) with Persisted with IntId {
				val id: Int = CategoryEntity.id
			}
	}

	object DogEntity extends Entity[IntId, Dog](classOf[Dog]) {
		val id = key("id") autogenerated (_.id)
		val name = column("name") option (_.name)

		def constructor(implicit m: ValuesMap) = new Dog(name) with Persisted with IntId {
			val id: Int = DogEntity.id
		}
	}

	val typeManager = new DefaultTypeManager()
	val typeRegistry = TypeRegistry(CategoryEntity)

	"manyToOneOption None=>null" in {
		CategoryEntity.parent.columnToValue(Category("x", None, None)) must beNull
	}

	"manyToOneOption Some(x)=>x" in {
		CategoryEntity.parent.columnToValue(Category("x", Some(Category("y", None, None)), None)) must_== Category("y", None, None)
	}

	"manyToOne/oneToOne constructor with None" in {
		val cat = Category("x", None, None)
		val newCat = CategoryEntity.constructor(ValuesMap.fromEntity(typeManager, CategoryEntity.tpe, cat))
		newCat must_== cat
	}

	"manyToOne constructor with Some" in {
		val cat = Category("x", Some(Category("y", None, None)), None)
		val newCat = CategoryEntity.constructor(ValuesMap.fromEntity(typeManager, CategoryEntity.tpe, cat))
		newCat must_== cat
	}

	"oneToOne None=>null" in {
		CategoryEntity.linked.columnToValue(Category("x", None, None)) must beNull
	}

	"oneToOneOption Some(x)=>x" in {
		CategoryEntity.linked.columnToValue(Category("x", None, Some(Category("y", None, None)))) must_== Category("y", None, None)
	}

	"oneToOne constructor with Some" in {
		val cat = Category("x", None, Some(Category("y", None, None)))
		val newCat = CategoryEntity.constructor(ValuesMap.fromEntity(typeManager, CategoryEntity.tpe, cat))
		newCat must_== cat
	}
}