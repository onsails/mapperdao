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
 *         22 Nov 2012
 */
class CmdToDatabase(
	updateConfig: UpdateConfig,
	driver: Driver,
	typeManager: TypeManager
) {

	private val jdbc = driver.jdbc

	private case class Node(
		sql: driver.sqlBuilder.Result,
		cmd: PersistCmd
	)

	def execute(cmdList: List[List[PersistCmd]]): List[PersistedNode[_, _]] = {

		// we need to flatten out the sql's so that we can batch process them
		// but also keep the tree structure so that we return only PersistedNode's
		// for the top level PersistedCmd's

		cmdList.map {
			cmds =>
				val nodes = toNodes(cmds)
				toDb(nodes)

				// now the batches were executed and we got a tree with
				// the commands and the autogenerated keys.
				toPersistedNodes(cmds)
		}.flatten
	}

	private def toDb(nodes: List[Node]) = {
		// group the sql's and batch-execute them
		nodes.groupBy {
			_.sql.sql
		}.foreach {
			case (sql, nodes) =>
				val cmd = nodes.head.cmd
				val args = nodes.map {
					case Node(sql, _) =>
						sql.values.toArray
				}.toArray

				cmd match {
					case cmdWe: CmdWithEntity[_, _] =>
						val tpe = cmdWe.tpe
						val table = tpe.table
						val autoGeneratedColumnNames = cmd match {
							case InsertCmd(_, _, _, _) =>
								table.autoGeneratedColumnNamesArray
							case _ => Array[String]()
						}
						val bo = BatchOptions(driver.batchStrategy(autoGeneratedColumnNames.length > 0), autoGeneratedColumnNames)

						// do the batch update
						val br = jdbc.batchUpdate(bo, sql, args)

						// now extract the keys and set them into the nodes
						if (br.keys != null) {
							val keys: Array[List[(SimpleColumn, Any)]] = br.keys.map {
								m: java.util.Map[String, Object] =>
									table.autoGeneratedColumns.map {
										column =>
											(
												column,
												table.pcColumnToColumnInfoMap(column) match {
													case ci: ColumnInfo[_, _] =>
														val value = driver.getAutoGenerated(m, column)
														typeManager.toActualType(ci.dataType, value)
												}
												)
									}
							}
							// note down the generated keys
							(nodes zip keys) foreach {
								case (node, keys) =>
									node.cmd match {
										case InsertCmd(_, newVM, _, _) =>
											newVM.addAutogeneratedKeys(keys)
										//										case UpdateCmd(_, _, newVM, _, _) =>
										//											newVM.addAutogeneratedKeys(keys)
									}
							}
						}
					case InsertManyToManyCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case DeleteManyToManyCmd(entity, foreignEntity, manyToMany, entityVM, foreignEntityVM) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
					case InsertManyToManyExternalCmd(_, _, _, _, _) | DeleteManyToManyExternalCmd(_, _, _, _, _) =>
						val bo = BatchOptions(driver.batchStrategy(false), Array())
						jdbc.batchUpdate(bo, sql, args)
				}
		}
	}

	private def toPersistedNodes(nodes: List[PersistCmd]) = nodes.collect {
		case InsertCmd(tpe, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, None, newVM, mainEntity)
		case UpdateCmd(tpe, oldVM, newVM, _, mainEntity) =>
			EntityPersistedNode(tpe, Some(oldVM), newVM, mainEntity)
		case InsertManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, foreignO) =>
			ExternalEntityPersistedNode(foreignEntity, foreignO, false)
		case UpdateExternalCmd(foreignEntity, manyToMany, fo) =>
			val ue = UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Update, fo)
			foreignEntity.manyToManyOnUpdateMap(manyToMany)(ue)
			ExternalEntityPersistedNode(foreignEntity, fo, false)
	}

	private def toNodes(cmds: List[PersistCmd]) = cmds.filterNot(_.blank).map {
		cmd =>
			val sql = toSql(cmd)
			Node(
				sql,
				cmd
			)
	}

	private def toSql(cmd: PersistCmd) = cmd match {
		case InsertCmd(tpe, o, columns, _) =>
			driver.insertSql(tpe, columns).result
		case UpdateCmd(tpe, oldVM, newVM, columns, _) =>
			val pks = oldVM.toListOfPrimaryKeyAndValueTuple(tpe)
			driver.updateSql(tpe, columns, pks).result
		case InsertManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
			val left = entityVM.toListOfPrimaryKeys(tpe)
			val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
			driver.insertManyToManySql(manyToMany, left, right).result
		case DeleteManyToManyCmd(tpe, foreignTpe, manyToMany, entityVM, foreignEntityVM) =>
			val left = entityVM.toListOfPrimaryKeys(tpe)
			val right = foreignEntityVM.toListOfPrimaryKeys(foreignTpe)
			driver.deleteManyToManySql(manyToMany, left, right).result
		case InsertManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, fo) =>
			val left = entityVM.toListOfPrimaryKeys(tpe)
			val ie = InsertExternalManyToMany(updateConfig, fo)
			val right = foreignEntity.manyToManyOnInsertMap(manyToMany)(ie)
			driver.insertManyToManySql(manyToMany.column, left, right.values).result
		case DeleteManyToManyExternalCmd(tpe, foreignEntity, manyToMany, entityVM, fo) =>
			val left = entityVM.toListOfPrimaryKeys(tpe)
			val de = UpdateExternalManyToMany(updateConfig, UpdateExternalManyToMany.Operation.Remove, fo)
			val right = foreignEntity.manyToManyOnUpdateMap(manyToMany)(de)
			driver.deleteManyToManySql(manyToMany.column, left, right.values).result
	}
}