/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eobjects.metamodel.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.eobjects.metamodel.MetaModelException;
import org.eobjects.metamodel.delete.AbstractRowDeletionBuilder;
import org.eobjects.metamodel.delete.RowDeletionBuilder;
import org.eobjects.metamodel.jdbc.dialects.IQueryRewriter;
import org.eobjects.metamodel.query.FilterItem;
import org.eobjects.metamodel.query.FromItem;
import org.eobjects.metamodel.schema.Table;
import org.eobjects.metamodel.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RowDeletionBuilder} that issues an SQL DELETE FROM statement
 * 
 * @author Kasper Sørensen
 */
final class JdbcDeleteBuilder extends AbstractRowDeletionBuilder {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDeleteBuilder.class);

    private final JdbcUpdateCallback _updateCallback;
    private final IQueryRewriter _queryRewriter;
    private final boolean _inlineValues;

    public JdbcDeleteBuilder(JdbcUpdateCallback updateCallback, Table table, IQueryRewriter queryRewriter) {
        this(updateCallback, table, queryRewriter, false);
    }

    public JdbcDeleteBuilder(JdbcUpdateCallback updateCallback, Table table, IQueryRewriter queryRewriter,
            boolean inlineValues) {
        super(table);
        _updateCallback = updateCallback;
        _queryRewriter = queryRewriter;
        _inlineValues = inlineValues;
    }

    @Override
    public void execute() throws MetaModelException {
        String sql = createSqlStatement();

        logger.debug("Delete statement created: {}", sql);
        final boolean reuseStatement = !_inlineValues;
        final PreparedStatement st = _updateCallback.getPreparedStatement(sql, reuseStatement);
        try {
            if (reuseStatement) {
                int valueCounter = 1;
                final List<FilterItem> whereItems = getWhereItems();
                for (FilterItem whereItem : whereItems) {
                    if (JdbcUtils.isPreparedParameterCandidate(whereItem)) {
                        Object operand = whereItem.getOperand();
                        st.setObject(valueCounter, operand);
                        valueCounter++;
                    }
                }
            }
            _updateCallback.executePreparedStatement(st, reuseStatement);
        } catch (SQLException e) {
            throw JdbcUtils.wrapException(e, "execute delete statement: " + sql);
        } finally {
            if (_inlineValues) {
                FileHelper.safeClose(st);
            }
        }
    }

    protected String createSqlStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("DELETE FROM ");
        sb.append(_queryRewriter.rewriteFromItem(new FromItem(getTable())));
        sb.append(JdbcUtils.createWhereClause(getWhereItems(), _queryRewriter, _inlineValues));
        return sb.toString();
    }

}
