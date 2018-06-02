/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.dbtest.common;

import io.shardingsphere.dbtest.asserts.DataSetDefinitions;
import io.shardingsphere.dbtest.config.bean.ParameterDefinition;
import io.shardingsphere.dbtest.config.bean.ParameterValueDefinition;
import io.shardingsphere.dbtest.config.dataset.init.DataSetColumnMetadata;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Database utility.
 * 
 * <p>
 * Database related operations a series of methods,
 * including pre-compiled sql processing,
 * non-precompiled sql processing and include result set transformation,
 * get table structure, etc.
 * </p>
 * 
 * @author liu ze jian
 * @author zhangliang
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatabaseUtil {
    
    /**
     * Execute update.
     *
     * @param connection connection
     * @param sql SQL
     * @throws SQLException SQL exception
     */
    public static void executeUpdate(final Connection connection, final String sql) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }
    
    /**
     * Execute batch.
     *
     * @param connection connection
     * @param sql SQL
     * @param sqlValueGroups SQL value groups
     * @throws SQLException SQL exception
     */
    public static void executeBatch(final Connection connection, final String sql, final List<SQLValueGroup> sqlValueGroups) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (SQLValueGroup each : sqlValueGroups) {
                setParameters(preparedStatement, each);
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        }
    }
    
    private static void setParameters(final PreparedStatement preparedStatement, final SQLValueGroup sqlValueGroup) throws SQLException {
        for (SQLValue each : sqlValueGroup.getSqlValues()) {
            preparedStatement.setObject(each.getIndex(), each.getValue());
        }
    }
    
    /**
     * Execute query for prepared statement.
     *
     * @param connection connection
     * @param sql SQL
     * @param sqlValues SQL values 
     * @return query result set
     * @throws SQLException SQL exception
     */
    public static DataSetDefinitions executeQueryForPreparedStatement(final Connection connection, final String sql, final Collection<SQLValue> sqlValues) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql.replaceAll("%s", "?"))) {
            for (SQLValue each : sqlValues) {
                preparedStatement.setObject(each.getIndex(), each.getValue());
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return getDatasetDefinition(resultSet);
            }
        }
    }
    
    /**
     * Execute DQL for prepared statement.
     *
     * @param connection connection
     * @param sql SQL
     * @param sqlValues SQL values
     * @return query result set
     * @throws SQLException SQL exception
     */
    public static DataSetDefinitions executeDQLForPreparedStatement(final Connection connection, final String sql, final Collection<SQLValue> sqlValues) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql.replaceAll("%s", "?"))) {
            for (SQLValue each : sqlValues) {
                preparedStatement.setObject(each.getIndex(), each.getValue());
            }
            assertTrue("Not a query statement.", preparedStatement.execute());
            try (ResultSet resultSet = preparedStatement.getResultSet()) {
                return getDatasetDefinition(resultSet);
            }
        }
    }
    
    /**
     * Execute DQL for statement.
     *
     * @param connection connection
     * @param sql SQL
     * @param sqlValues SQL values
     * @return query result set
     * @throws SQLException SQL exception
     */
    public static DataSetDefinitions executeQueryForStatement(final Connection connection, final String sql, final Collection<SQLValue> sqlValues) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(sqlStatement(sql, sqlValues))) {
                return getDatasetDefinition(resultSet);
            }
        }
    }
    
    /**
     * Use Statement test SQL select.
     *
     * @param connection connection
     * @param sql SQL
     * @param sqlValues SQL values
     * @return query result set
     * @throws SQLException SQL exception
     */
    public static DataSetDefinitions executeDQLForStatement(final Connection connection, final String sql, final Collection<SQLValue> sqlValues) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            assertTrue("Not a query statement.", statement.execute(sqlStatement(sql, sqlValues)));
            try (ResultSet resultSet = statement.getResultSet()) {
                return getDatasetDefinition(resultSet);
            }
        }
    }
    
    /**
     * Use Statement Test data update.
     *
     * @param connection connection
     * @param sql SQL
     * @param parameterDefinition parameter
     * @return Number of rows as a result of execution
     * @throws SQLException SQL exception
     */
    public static int updateUseStatementToExecuteUpdate(final Connection connection, final String sql, final ParameterDefinition parameterDefinition) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sqlStatement0(sql, parameterDefinition.getValues()));
        }
    }
    
    private static String sqlStatement(final String sql, final Collection<SQLValue> sqlValues) {
        if (null == sqlValues) {
            return sql;
        }
        String result = sql;
        for (SQLValue each : sqlValues) {
            result = Pattern.compile("%s", Pattern.LITERAL).matcher(result)
                    .replaceFirst(Matcher.quoteReplacement(each.getValue() instanceof String ? "'" + each.getValue() + "'" : each.getValue().toString()));
        }
        return result;
    }
    
    private static String sqlStatement0(final String sql, final List<ParameterValueDefinition> parameter) {
        if (null == parameter) {
            return sql;
        }
        String result = sql;
        for (ParameterValueDefinition each : parameter) {
            String type = each.getType();
            String dataColumn = each.getValue();
            switch (type) {
                case "byte":
                case "short":
                case "int":
                case "long":
                case "float":
                case "double":
                    result = Pattern.compile("%s", Pattern.LITERAL).matcher(result).replaceFirst(Matcher.quoteReplacement(dataColumn));
                    break;
                case "boolean":
                    result = Pattern.compile("%s", Pattern.LITERAL).matcher(result).replaceFirst(Matcher.quoteReplacement(Boolean.valueOf(dataColumn).toString()));
                    break;
                default:
                    result = Pattern.compile("%s", Pattern.LITERAL).matcher(result).replaceFirst(Matcher.quoteReplacement("'" + dataColumn + "'"));
                    break;
            }
        }
        return result;
    }
    
    /**
     * Use Statement Test data update.
     *
     * @param connection connection
     * @param sql SQL
     * @param parameterDefinition parameter definition
     * @return implementation results
     * @throws SQLException SQL exception
     */
    public static int updateUseStatementToExecute(final Connection connection, final String sql, final ParameterDefinition parameterDefinition) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!statement.execute(sqlStatement0(sql, parameterDefinition.getValues()))) {
                return statement.getUpdateCount();
            }
        }
        return 0;
    }
    
    /**
     * Use PreparedStatement test data update.
     *
     * @param connection connection
     * @param sql SQL
     * @param parameterDefinition parameter
     * @return Number of rows as a result of execution
     * @throws SQLException SQL exception
     * @throws ParseException parse exception
     */
    public static int updateUsePreparedStatementToExecuteUpdate(final Connection connection, final String sql, final ParameterDefinition parameterDefinition) throws SQLException, ParseException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql.replaceAll("%s", "?"))) {
            sqlPreparedStatement0(parameterDefinition.getValues(), preparedStatement);
            return preparedStatement.executeUpdate();
        }
    }
    
    /**
     * Use PreparedStatement test data update.
     *
     * @param connection connection
     * @param sql SQL
     * @param parameterDefinition parameter definition
     * @return implementation results
     * @throws SQLException   SQL exception
     * @throws ParseException parse exception
     */
    public static int updateUsePreparedStatementToExecute(final Connection connection, final String sql, final ParameterDefinition parameterDefinition) throws SQLException, ParseException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql.replaceAll("%s", "?"))) {
            sqlPreparedStatement0(parameterDefinition.getValues(), preparedStatement);
            if (!preparedStatement.execute()) {
                return preparedStatement.getUpdateCount();
            }
        }
        return 0;
    }
    
    /**
     * Use PreparedStatement test SQL select.
     *
     * @param conn connection
     * @param sql SQL
     * @param parameterDefinition parameter definition 
     * @return query result set
     * @throws SQLException   SQL exception
     * @throws ParseException parse exception
     */
    public static DataSetDefinitions selectUsePreparedStatement0(final Connection conn, final String sql, final ParameterDefinition parameterDefinition) throws SQLException, ParseException {
        try (PreparedStatement preparedStatement = conn.prepareStatement(sql.replaceAll("%s", "?"))) {
            sqlPreparedStatement0(parameterDefinition.getValues(), preparedStatement);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return getDatasetDefinition(resultSet);
            }
        }
    }
    
    private static DataSetDefinitions getDatasetDefinition(final ResultSet resultSet) throws SQLException {
        List<DataSetColumnMetadata> dataSetColumnMetadataList = getDataSetColumnMetadataList(resultSet);
        Map<String, List<DataSetColumnMetadata>> configs = new HashMap<>();
        configs.put("data", dataSetColumnMetadataList);
        Map<String, List<Map<String, String>>> dataMap = new HashMap<>();
        dataMap.put("data", handleResultSet(resultSet, dataSetColumnMetadataList));
        return new DataSetDefinitions(configs, dataMap);
    }
    
    private static List<DataSetColumnMetadata> getDataSetColumnMetadataList(final ResultSet resultSet) throws SQLException {
        List<DataSetColumnMetadata> result = new LinkedList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i < columnCount + 1; i++) {
            DataSetColumnMetadata each = new DataSetColumnMetadata();
            each.setName(metaData.getColumnName(i));
            each.setType(getDataType(metaData.getColumnType(i), metaData.getScale(i)));
            result.add(each);
        }
        return result;
    }
    
    private static List<Map<String, String>> handleResultSet(final ResultSet resultSet, final Collection<DataSetColumnMetadata> dataSetColumnMetadataList) throws SQLException {
        List<Map<String, String>> result = new LinkedList<>();
        while (resultSet.next()) {
            Map<String, String> data = new HashMap<>();
            for (DataSetColumnMetadata each : dataSetColumnMetadataList) {
                String name = each.getName();
                if ("Date".equals(each.getType())) {
                    data.put(name, new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(resultSet.getDate(name).getTime())));
                } else {
                    data.put(name, String.valueOf(resultSet.getObject(name)));
                }
            }
            result.add(data);
        }
        return result;
    }
    
    private static void sqlPreparedStatement0(final List<ParameterValueDefinition> parameterValueDefinitions, final PreparedStatement preparedStatement) throws SQLException, ParseException {
        if (null == parameterValueDefinitions) {
            return;
        }
        int index = 0;
        for (ParameterValueDefinition each : parameterValueDefinitions) {
            SQLValue sqlValue = new SQLValue(each.getValue(), each.getType(), ++index);
            preparedStatement.setObject(sqlValue.getIndex(), sqlValue.getValue());
        }
    }
    
    private static String getDataType(final int type, final int scale) {
        String result;
        switch (type) {
            case Types.BOOLEAN:
                result = "boolean";
                break;
            case Types.CHAR:
                result = "char";
                break;
            case Types.NUMERIC:
                switch (scale) {
                    case 0:
                        result = "double";
                        break;
                    case -127:
                        result = "float";
                        break;
                    default:
                        result = "double";
                }
                break;
            case Types.DATE:
                result = "Date";
                break;
            case Types.TIMESTAMP:
                result = "Date";
                break;
            case Types.BLOB:
                result = "Blob";
                break;
            default:
                result = getDataTypeMore(type);
                break;
        }
        return result;
    }
    
    private static String getDataTypeMore(final int type) {
        switch (type) {
            case Types.INTEGER:
                return "int";
            case Types.LONGVARCHAR:
                return "long";
            case Types.BIGINT:
                return "long";
            case Types.FLOAT:
                return "float";
            case Types.DOUBLE:
                return "double";
            default:
                return "String";
        }
    }
    
    /**
     * Comparative data set.
     *
     * @param expected expected
     * @param actual actual
     * @param table table
     */
    public static void assertConfigs(final DataSetDefinitions expected, final List<DataSetColumnMetadata> actual, final String table) {
        Map<String, List<DataSetColumnMetadata>> configs = expected.getMetadataList();
        List<DataSetColumnMetadata> columnDefinitions = configs.get(table);
        for (DataSetColumnMetadata each : columnDefinitions) {
            checkActual(actual, each);
        }
    }
    
    private static void checkActual(final List<DataSetColumnMetadata> actual, final DataSetColumnMetadata expect) {
        for (DataSetColumnMetadata each : actual) {
            if (expect.getName().equals(each.getName())) {
                if (StringUtils.isNotEmpty(expect.getType())) {
                    assertEquals(expect.getType(), each.getType());
                }
                checkDatabaseColumn(expect.getDecimalDigits(), each.getDecimalDigits());
                checkDatabaseColumn(expect.getNullable(), each.getNullable());
                checkDatabaseColumn(expect.getNumPrecRadix(), each.getNumPrecRadix());
                checkDatabaseColumn(expect.getSize(), each.getSize());
            }
        }
    }
    
    private static void checkDatabaseColumn(final Integer expectData, final Integer actualData) {
        if (expectData != null && !expectData.equals(actualData)) {
            fail();
        }
    }
    
    /**
     * Use PreparedStatement test SQL select.
     *
     * @param connection connection
     * @param table table
     * @return query result set
     * @throws SQLException SQL exception
     */
    public static List<DataSetColumnMetadata> getColumnDefinitions(final Connection connection, final String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, table, null)) {
            List<DataSetColumnMetadata> result = new ArrayList<>();
            while (resultSet.next()) {
                DataSetColumnMetadata columnDefinition = new DataSetColumnMetadata();
                String column = resultSet.getString("COLUMN_NAME");
                columnDefinition.setName(column);
                int size = resultSet.getInt("COLUMN_SIZE");
                columnDefinition.setSize(size);
                String columnType = resultSet.getString("TYPE_NAME").toLowerCase();
                columnDefinition.setType(columnType);
                int decimalDigits = resultSet.getInt("DECIMAL_DIGITS");
                columnDefinition.setDecimalDigits(decimalDigits);
                int numPrecRadix = resultSet.getInt("NUM_PREC_RADIX");
                columnDefinition.setNumPrecRadix(numPrecRadix);
                int nullAble = resultSet.getInt("NULLABLE");
                columnDefinition.setNullable(nullAble);
                String isAutoincrement = resultSet.getString("IS_AUTOINCREMENT");
                if (StringUtils.isNotEmpty(isAutoincrement)) {
                    columnDefinition.setAutoIncrement(true);
                }
                result.add(columnDefinition);
            }
            return result;
        }
    }
}
