package com.googlecode.mapperdao

import org.specs2.mutable.SpecificationWithJUnit
import org.junit.runner.RunWith
import org.junit.runners.Suite.SuiteClasses
import org.junit.runners.Suite

/**
 * run all specs within the IDE
 *
 * Note:this won't run when building via maven, as surefire will run each test separately
 *
 * @author kostantinos.kougios
 *
 * 6 Aug 2011
 */
@RunWith(classOf[Suite])
@SuiteClasses(
	Array(
		classOf[utils.EqualitySpec],
		classOf[utils.TraversableSeparationSpec],
		classOf[EntityMapSpec],
		classOf[ManyToManyAutoGeneratedSpec],
		classOf[ManyToManyMutableAutoGeneratedSpec],
		classOf[ManyToManyNonRecursiveSpec],
		classOf[ManyToManyQuerySpec],
		classOf[ManyToManyQueryWithAliasesSpec],
		classOf[ManyToManySpec],
		classOf[ManyToOneAndOneToManyCyclicSpec],
		classOf[ManyToOneAndOneToManyCyclicAutoGeneratedSpec],
		classOf[ManyToOneAutoGeneratedSpec],
		classOf[ManyToOneMutableAutoGeneratedSpec],
		classOf[SimpleTypesSpec],
		classOf[OneToManyAutoGeneratedSpec],
		classOf[OneToManySpec],
		classOf[OneToManySelfReferencedSpec],
		classOf[OneToOneMutableTwoWaySpec],
		classOf[OneToOneImmutableOneWaySpec],
		classOf[OneToOneAutogeneratedTwoWaySpec],
		classOf[ManyToOneSpec],
		classOf[SimpleQuerySpec],
		classOf[ManyToOneQuerySpec],
		classOf[SimpleSelfJoinQuerySpec],
		classOf[SimpleTypeAutoGeneratedSpec],
		classOf[ManyToOneSelfJoinQuerySpec],
		classOf[OneToManyQuerySpec],
		classOf[OneToOneQuerySpec],
		classOf[OneToOneWithoutReverseSpec],
		classOf[TwoPrimaryKeysSimpleSpec],
		classOf[IntermediateImmutableEntityWithStringFKsSpec],
		classOf[jdbc.JdbcSpec],
		classOf[jdbc.TransactionSpec],
		classOf[utils.DaoMixinsSpec],
		classOf[CRUDConfigsSpec],
		classOf[MemoryMapperDaoSpec],
		classOf[MockMapperDaoSpec]
	)
)
class All extends SpecificationWithJUnit