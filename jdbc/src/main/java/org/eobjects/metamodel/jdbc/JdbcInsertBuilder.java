/**
 * eobjects.org MetaModel
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.metamodel.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;

import org.eobjects.metamodel.insert.AbstractRowInsertionBuilder;
import org.eobjects.metamodel.insert.RowInsertionBuilder;
import org.eobjects.metamodel.jdbc.dialects.IQueryRewriter;
import org.eobjects.metamodel.query.FromItem;
import org.eobjects.metamodel.schema.Column;
import org.eobjects.metamodel.schema.Table;
import org.eobjects.metamodel.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RowInsertionBuilder} that issues an SQL INSERT statement
 * 
 * @author Kasper Sørensen
 */
final class JdbcInsertBuilder extends AbstractRowInsertionBuilder<JdbcUpdateCallback> {

	private static final Logger logger = LoggerFactory.getLogger(JdbcInsertBuilder.class);

	private final boolean _inlineValues;
	private final IQueryRewriter _queryRewriter;

	public JdbcInsertBuilder(JdbcUpdateCallback updateCallback, Table table, IQueryRewriter queryRewriter) {
		this(updateCallback, table, false, queryRewriter);
	}

	public JdbcInsertBuilder(JdbcUpdateCallback updateCallback, Table table, boolean isInlineValues,
			IQueryRewriter queryRewriter) {
		super(updateCallback, table);
		if (!(table instanceof JdbcTable)) {
			throw new IllegalArgumentException("Not a valid JDBC table: " + table);
		}

		_inlineValues = isInlineValues;
		_queryRewriter = queryRewriter;
	}

	@Override
	public void execute() {
		final String sql = createSqlStatement();
		if (logger.isDebugEnabled()) {
			logger.debug("Inserting: {}", Arrays.toString(getValues()));
			logger.debug("Insert statement created: {}", sql);
		}
		final JdbcUpdateCallback updateCallback = getUpdateCallback();
		final boolean reuseStatement = !_inlineValues;
		final PreparedStatement st = updateCallback.getPreparedStatement(sql, reuseStatement);
		try {
			if (reuseStatement) {
				Column[] columns = getColumns();
				Object[] values = getValues();
				boolean[] explicitNulls = getExplicitNulls();
				int valueCounter = 1;
				for (int i = 0; i < columns.length; i++) {
					boolean explicitNull = explicitNulls[i];
					if (values[i] != null || explicitNull) {
						JdbcUtils.setStatementValue(st, valueCounter, columns[i], values[i]);
						valueCounter++;
					}
				}
			}
			updateCallback.executePreparedStatement(st, reuseStatement);
		} catch (SQLException e) {
			throw JdbcUtils.wrapException(e, "execute insert statement: " + sql);
		} finally {
			if (_inlineValues) {
				FileHelper.safeClose(st);
			}
		}
	}
	
	protected String createSqlStatement() {
	    return createSqlStatement(_inlineValues);
	}

	private String createSqlStatement(boolean inlineValues) {
		final Object[] values = getValues();
		final Table table = getTable();
		final StringBuilder sb = new StringBuilder();

		final String tableLabel = _queryRewriter.rewriteFromItem(new FromItem(table));

		sb.append("INSERT INTO ");
		sb.append(tableLabel);
		sb.append(" (");
		Column[] columns = getColumns();
		boolean[] explicitNulls = getExplicitNulls();
		boolean firstValue = true;
		for (int i = 0; i < columns.length; i++) {
			if (values[i] != null || explicitNulls[i]) {
				if (firstValue) {
					firstValue = false;
				} else {
					sb.append(',');
				}
				String columnName = columns[i].getName();
				columnName = getUpdateCallback().quoteIfNescesary(columnName);
				sb.append(columnName);
			}
		}

		sb.append(") VALUES (");
		firstValue = true;
		for (int i = 0; i < columns.length; i++) {
			if (values[i] != null || explicitNulls[i]) {
				if (firstValue) {
					firstValue = false;
				} else {
					sb.append(',');
				}
				if (inlineValues) {
					sb.append(JdbcUtils.getValueAsSql(columns[i], values[i], _queryRewriter));
				} else {
					sb.append('?');
				}
			}
		}
		sb.append(")");
		String sql = sb.toString();
		return sql;
	}

	@Override
	public String toSql() {
	    return createSqlStatement(true);
	}
}
