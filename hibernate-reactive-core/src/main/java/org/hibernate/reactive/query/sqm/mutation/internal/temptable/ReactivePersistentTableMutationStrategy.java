/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.reactive.query.sqm.mutation.internal.temptable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.mapping.internal.MappingModelCreationProcess;
import org.hibernate.query.spi.DomainQueryExecutionContext;
import org.hibernate.query.sqm.internal.DomainParameterXref;
import org.hibernate.query.sqm.mutation.internal.temptable.PersistentTableMutationStrategy;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.hibernate.reactive.query.sqm.mutation.spi.ReactiveSqmMultiTableMutationStrategy;


public class ReactivePersistentTableMutationStrategy extends PersistentTableMutationStrategy
		implements ReactivePersistentTableStrategy, ReactiveSqmMultiTableMutationStrategy {

	private final CompletableFuture<Void> tableCreatedStage = new CompletableFuture();

	private final CompletableFuture<Void> tableDroppedStage = new CompletableFuture();

	private boolean prepared;

	private boolean dropIdTables;

	public ReactivePersistentTableMutationStrategy(PersistentTableMutationStrategy original) {
		super( original.getTemporaryTable(), original.getSessionFactory() );
	}

	private static String sessionIdentifier(SharedSessionContractImplementor session) {
		return session.getSessionIdentifier().toString();
	}

	@Override
	public void prepare(
			MappingModelCreationProcess mappingModelCreationProcess,
			JdbcConnectionAccess connectionAccess) {
		prepare( mappingModelCreationProcess, connectionAccess, tableCreatedStage );
	}

	@Override
	public void release(SessionFactoryImplementor sessionFactory, JdbcConnectionAccess connectionAccess) {
		release( sessionFactory, connectionAccess, tableDroppedStage );
	}

	@Override
	public CompletionStage<Integer> reactiveExecuteUpdate(
			SqmUpdateStatement<?> sqmUpdateStatement,
			DomainParameterXref domainParameterXref,
			DomainQueryExecutionContext context) {
		return tableCreatedStage
				.thenCompose( v -> new ReactiveTableBasedUpdateHandler(
						sqmUpdateStatement,
						domainParameterXref,
						getTemporaryTable(),
						getSessionFactory().getJdbcServices().getDialect().getTemporaryTableAfterUseAction(),
						ReactivePersistentTableMutationStrategy::sessionIdentifier,
						getSessionFactory()
				).reactiveExecute( context ) );
	}

	@Override
	public CompletionStage<Integer> reactiveExecuteDelete(
			SqmDeleteStatement<?> sqmDeleteStatement,
			DomainParameterXref domainParameterXref,
			DomainQueryExecutionContext context) {
		return tableCreatedStage
				.thenCompose( v -> new ReactiveTableBasedDeleteHandler(
						sqmDeleteStatement,
						domainParameterXref,
						getTemporaryTable(),
						getSessionFactory().getJdbcServices().getDialect().getTemporaryTableAfterUseAction(),
						ReactivePersistentTableMutationStrategy::sessionIdentifier,
						getSessionFactory()
				).reactiveExecute( context ) );
	}

	@Override
	public CompletionStage<Void> getDropTableActionStage() {
		return tableDroppedStage;
	}

	@Override
	public CompletionStage<Void> getCreateTableActionStage() {
		return tableCreatedStage;
	}

	@Override
	public boolean isPrepared() {
		return prepared;
	}

	@Override
	public void setPrepared(boolean prepared) {
		this.prepared = prepared;
	}

	@Override
	public boolean isDropIdTables() {
		return dropIdTables;
	}

	@Override
	public void setDropIdTables(boolean dropIdTables) {
		this.dropIdTables = dropIdTables;
	}
}
