package com.googlecode.mapperdao.jdbc

import com.googlecode.mapperdao._
import com.googlecode.mapperdao.state.persistcmds._
import com.googlecode.mapperdao.drivers.Driver
import org.springframework.jdbc.core.SqlParameterValue
import com.googlecode.mapperdao.state.persisted._
import com.googlecode.mapperdao.state.persistcmds.PersistCmd
import com.googlecode.mapperdao.state.persistcmds.InsertCmd

/**
 * converts commands to database operations, executes
 * them and returns the resulting persisted nodes.
 *
 * @author kostantinos.kougios
 *
 * 22 Nov 2012
 */
class CmdToDatabase(
		updateConfig: UpdateConfig,
		driver: Driver,
		typeManager: TypeManager) {

	private val jdbc = driver.jdbc

	private case class Node[ID, T](
			sql: driver.sqlBuilder.Result,
			cmd: PersistCmd[ID, T],
			children: List[Node[_, _]],
			var keys: List[(SimpleColumn, Any)]) {

		def flatten: List[Node[_, _]] = this :: children.map { c => c.flatten }.flatten
	}

	def execute[ID, PC <: DeclaredIds[ID], T](
		cmds: List[PersistCmd[ID, T]]): List[PersistedNode[ID, T]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		val tree = toNodes(cmds)
		val nodes = tree.map { _.flatten }.flatten

		// group the sql's and batch-execute them
		nodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val entity = nodes.head.cmd.entity
				val table = entity.tpe.table
				val autoGeneratedColumnNames = table.autoGeneratedColumnNamesArray
				val bo = BatchOptions(driver.batchStrategy, autoGeneratedColumnNames)
				val args = nodes.map {
					case Node(sql, _, _, _) =>
						sql.values.toArray
				}.toArray

				// do the batch update
				val br = jdbc.batchUpdate(bo, sql, args)

				// now extract the keys and set them into the nodes
				if (br.keys != null) {
					val keys = br.keys map { m =>
						table.autoGeneratedColumns.map { column =>
							(column, driver.getAutoGenerated(m, column))
						}
					}
					// note down the generated keys
					(nodes zip keys) foreach {
						case (node, keys) =>
							node.keys = keys
					}
				}
		}

		// now the batches were executed and we got a tree with 
		// the commands and the autogenerated keys.
		toPersistedNodes(tree)
	}

	private def toPersistedNodes[ID, T](nodes: List[Node[ID, T]]) = nodes.map { node =>
		val cmd = node.cmd
		cmd match {
			case i: InsertCmd[ID, T] =>
				PersistedNode(i.entity, null, i.newVM, Nil, node.keys)
			case u: UpdateCmd[ID, T] =>
				PersistedNode(u.entity, u.oldVM, u.newVM, Nil, node.keys)
		}
	}

	private def toNodes[ID, T](
		cmds: List[PersistCmd[ID, T]]) = cmds.map { cmd =>

		def convert[ID, T](cmd: PersistCmd[ID, T]): Node[ID, T] = {
			val sql = toSql(cmd)
			Node(
				sql,
				cmd,
				(
					cmd.commands.map { c =>
						convert(c)
					}
				),
				Nil
			)
		}

		convert(cmd)
	}

	private def toSql(cmd: PersistCmd[_, _]) = cmd match {
		case InsertCmd(entity, o, columns, commands) =>
			driver.insertSql(entity.tpe, columns).result
	}
}