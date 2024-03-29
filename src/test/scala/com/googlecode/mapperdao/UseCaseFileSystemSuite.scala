package com.googlecode.mapperdao

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.googlecode.mapperdao.jdbc.Setup

/**
 * @author kostantinos.kougios
 *
 *         7 Feb 2012
 */
@RunWith(classOf[JUnitRunner])
class UseCaseFileSystemSuite extends FunSuite with ShouldMatchers
{

	import UseCaseFileSystemSuite._

	val (jdbc, mapperDao, queryDao) = Setup.setupMapperDao(List(DirectoryEntity, FileEntity, ArchiveEntity))

	if (Setup.database == "h2") {
		test("integration") {
			createTables()
			val parent = Directory("parent_dir", None, Nil)
			val dir = Directory("my_dir", Some(parent), Nil)
			val inserted = mapperDao.insert(DirectoryEntity, dir)
			val file = File("file1.txt", inserted, "text file")
			val archive = Archive("f.zip", inserted, "zip")
			val toUpdate = inserted.copy(nodes = List(file, archive, Archive("f2.gz", inserted, "gz")))
			val updated = mapperDao.update(DirectoryEntity, inserted, toUpdate)
			updated should be === toUpdate
			val selected = mapperDao.select(DirectoryEntity, inserted.id).get
			// the file into the directory contains a mock of the directory itself.
			// that mock is not fully populated and hence selected!=updated because
			// the updated doesn't contain the mock. We need to check the properties
			// one by one, avoiding the mock.
			selected.uri should be === updated.uri
			selected.parent should be === updated.parent
			selected.nodes.head.uri should be === updated.nodes.head.uri
			(selected.nodes.head match {
				case f: File => f.fileType
			}) should be === file.fileType
			(selected.nodes.tail.head match {
				case a: Archive => a.zipType
			}) should be === archive.zipType

			// now remove the archives
			val reUpdated = mapperDao.update(DirectoryEntity, updated, updated.copy(nodes = updated.nodes.collect {
				case f: File => f
			}))
			val reSelected = mapperDao.select(DirectoryEntity, reUpdated.id).get
			reSelected.nodes.size should be === 1
			(reSelected.nodes.head match {
				case f: File => f.fileType
			}) should be === file.fileType
		}

		test("persist directory") {
			createTables()
			val dir = Directory("my_dir", None, Nil)
			val inserted = mapperDao.insert(DirectoryEntity, dir)
			dir should be === inserted
		}

		test("persist directory with parent") {
			createTables()
			val parent = Directory("parent_dir", None, Nil)
			val dir = Directory("my_dir", Some(parent), Nil)
			val inserted = mapperDao.insert(DirectoryEntity, dir)
			dir should be === inserted
		}
	}

	def createTables() {
		Setup.dropAllTables(jdbc)
		val queries = Setup.queries(this, jdbc)
		queries.update("ddl")
	}
}

object UseCaseFileSystemSuite
{

	/**
	 * Domain model classes
	 */
	abstract class Node(val uri: String)

	case class Directory(override val uri: String, parent: Option[Directory], nodes: List[Node]) extends Node(uri)

	abstract class FileNode(override val uri: String, val parent: Directory) extends Node(uri)

	case class File(override val uri: String, override val parent: Directory, fileType: String) extends FileNode(uri, parent)

	case class Archive(override val uri: String, override val parent: Directory, zipType: String) extends FileNode(uri, parent)

	/**
	 * Entities
	 */

	/**
	 * the parent entity of all allNodes, used only for subclassing. It contains all common columns
	 */
	abstract class NodeEntity[T <: Node](clz: Class[T]) extends Entity[Int, SurrogateIntId, T](clz.getSimpleName, clz)
	{

		val id = key("id") autogenerated (_.id)
		val uri = column("uri") to (_.uri)
	}

	object DirectoryEntity extends NodeEntity(classOf[Directory])
	{
		// we need to map each node seperatelly. Lets start with files
		val files = onetomany(FileEntity) foreignkey ("parent_id") to (_.nodes.collect {
			// we'll collect only the files
			case f: File => f
		})
		// and continue with the archives
		var archives = onetomany(ArchiveEntity) foreignkey ("parent_id") to (_.nodes.collect {
			// we'll collect only the archives
			case a: Archive => a
		})

		val parent = manytoone(this) foreignkey ("parent_id") option (_.parent)

		// though we map files and archives separatelly, for the domain model we need to
		// merge them into a node list:
		def constructor(implicit m) = {
			val fList = m(files).toList ++ m(archives).toList
			new Directory(uri, parent, fList) with Stored
			{
				val id: Int = DirectoryEntity.id
			}
		}
	}

	abstract class FileNodeEntity[T <: FileNode](clz: Class[T]) extends NodeEntity(clz)
	{
		val parent = manytoone(DirectoryEntity) foreignkey ("parent_id") to (_.parent)
	}

	object FileEntity extends FileNodeEntity(classOf[File])
	{
		val fileType = column("fileType") to (_.fileType)

		def constructor(implicit m) = new File(uri, parent, fileType) with Stored
		{
			val id: Int = FileEntity.id
		}
	}

	object ArchiveEntity extends FileNodeEntity(classOf[Archive])
	{
		val zipType = column("zipType") to (_.zipType)

		def constructor(implicit m) = new Archive(uri, parent, zipType) with Stored
		{
			val id: Int = ArchiveEntity.id
		}
	}

}