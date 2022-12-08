/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.jdbc.thin;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.myservice.MyLoadScriptService;
import cn.myservice.MySqlAstService;
import cn.mysuper.service.IMySqlAst;
import cn.smart.service.IMyLoadScript;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.Query;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.internal.processors.cache.query.IgniteQueryErrorCode;
import org.apache.ignite.internal.processors.odbc.ClientListenerResponse;
import org.apache.ignite.internal.processors.odbc.SqlStateCode;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcBatchExecuteRequest;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcBatchExecuteResult;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcBulkLoadAckResult;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcBulkLoadBatchRequest;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcQuery;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcQueryCancelRequest;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcQueryExecuteMultipleStatementsResult;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcQueryExecuteRequest;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcQueryExecuteResult;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcResult;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcResultInfo;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcResultWithIo;
import org.apache.ignite.internal.processors.odbc.jdbc.JdbcStatementType;
import org.apache.ignite.internal.processors.query.IgniteSQLException;
import org.apache.ignite.internal.sql.SqlKeyword;
import org.apache.ignite.internal.sql.SqlParseException;
import org.apache.ignite.internal.sql.SqlParser;
import org.apache.ignite.internal.sql.command.SqlCommand;
import org.apache.ignite.internal.sql.command.SqlSetStreamingCommand;
import org.apache.ignite.internal.util.HostAndPortRange;
import org.apache.ignite.internal.util.typedef.F;
import org.tools.MyLineToBinary;

import static java.sql.ResultSet.CONCUR_READ_ONLY;
import static java.sql.ResultSet.FETCH_FORWARD;
import static java.sql.ResultSet.TYPE_FORWARD_ONLY;

/**
 * JDBC statement implementation.
 */
public class JdbcThinStatement implements Statement {
    /** Default queryPage size. */
    private static final int DFLT_PAGE_SIZE = Query.DFLT_PAGE_SIZE;

    /** JDBC Connection implementation. */
    protected final JdbcThinConnection conn;

    /** Schema name. */
    private final String schema;

    /** Closed flag. */
    private volatile boolean closed;

    /** Rows limit. */
    private int maxRows;

    /** Query timeout. */
    private int timeout;

    /** Explicit timeout ({@code true} is the timeout is set explicitly for the query. Otherwise {@code false}). */
    boolean explicitTimeout;

    /** Request timeout. */
    private int reqTimeout;

    /** Fetch size. */
    private int pageSize = DFLT_PAGE_SIZE;

    /** Result set holdability. */
    private final int resHoldability;

    /** Batch size to keep track of number of items to return as fake update counters for executeBatch. */
    protected int batchSize;

    /** Batch. */
    protected List<JdbcQuery> batch;

    /** Close this statement on result set close. */
    private boolean closeOnCompletion;

    /** Result sets. */
    protected volatile List<JdbcThinResultSet> resultSets;

    /** Current result index. */
    protected int curRes;

    /** Current request Id. */
    private long currReqId;

    /** Current cliIo. */
    private JdbcThinTcpIo stickyIo;

    /** Cancelled flag. */
    private volatile boolean cancelled;

    /** Cancellation mutex. */
    final Object cancellationMux = new Object();

    private IMySqlAst mySqlAst = getMySqlAst(); //MySqlAstService.getInstance().getMySqlAst();

    private IMyLoadScript myLoadScript = getMyLoadScript(); //MyLoadScriptService.getInstance().getMyLoadScript();

    private IgniteClient client;

    private IMySqlAst getMySqlAst()
    {
        IMySqlAst mySqlAst = null;
        try {
            mySqlAst = MySqlAstService.getInstance().getMySqlAst();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return mySqlAst;
    }

    private IMyLoadScript getMyLoadScript()
    {
        IMyLoadScript myLoadScript = null;
        try {
            myLoadScript = MyLoadScriptService.getInstance().getMyLoadScript();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return myLoadScript;
    }

    private void saveMlData(final String schema_name, final String table_name, final String value) throws SQLException {
        myLoadScript.loadCsv(client, schema_name, table_name, value);
    }

    private List<List<String>> reList(final List<List<String>> lsts) throws SQLException {
        List<List<String>> myLsts = new ArrayList<>();
        for (List<String> lst : lsts)
        {
            if (lst.size() > 0 && lst.get(0).equals("loadFromNative"))
            {
                String path = mySqlAst.getStrValue(lst.subList(2, lst.size() - 1).get(0));
                String code = myLoadScript.loadFromNative(path);
                String[] myLoadFromNative = new String[]{"loadCode", "(", code, ")", ";"};
                myLsts.add(Arrays.asList(myLoadFromNative));
            }
            else if (lst.size() > 0 && lst.get(0).equals("loadCsv"))
            {
                if(lst.get(lst.size() - 1).equals(";"))
                {
                    List<String> lst_1 = lst.subList(2, lst.size() - 2);
                    StringBuilder sb = new StringBuilder();
                    for (String line : lst_1)
                    {
                        sb.append(line);
                    }

                    Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
                    Map<String, String> csvMap = gson.fromJson(sb.toString(), new TypeToken<Hashtable<String, String>>() {}.getType());

                    String code = myLoadScript.loadFromNative(csvMap.get("csv_path"));
                    String[] vs = code.split("\n|\r");
                    if (vs.length > 0) {
                        String schema_name = csvMap.get("schema_name").toLowerCase();
                        String table_name = csvMap.get("table_name").toLowerCase();
                        for (String dv : vs) {
                            saveMlData(schema_name, table_name, dv);
                        }
                    }

                    List<String> lst_3 = new ArrayList<>();
                    lst_3.add("println");
                    lst_3.add("(");
                    lst_3.add("'上传数据成功！'");
                    lst_3.add(")");
                    lst_3.add(";");
                    myLsts.add(lst_3);

                }
                else if(lst.get(lst.size() - 1).equals(")"))
                {
                    List<String> lst_1 = lst.subList(2, lst.size() - 1);
                    StringBuilder sb = new StringBuilder();
                    for (String line : lst_1)
                    {
                        sb.append(line);
                    }

                    Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
                    Map<String, String> csvMap = gson.fromJson(sb.toString(), new TypeToken<Hashtable<String, String>>() {}.getType());

                    String code = myLoadScript.loadFromNative(csvMap.get("csv_path"));
                    String[] vs = code.split("\n|\r");
                    if (vs.length > 0) {
                        String schema_name = csvMap.get("schema_name").toLowerCase();
                        String table_name = csvMap.get("table_name").toLowerCase();
                        for (String dv : vs) {
                            saveMlData(schema_name, table_name, dv);
                        }
                    }

                    List<String> lst_3 = new ArrayList<>();
                    lst_3.add("println");
                    lst_3.add("(");
                    lst_3.add("'上传数据成功！'");
                    lst_3.add(")");
                    lst_3.add(";");
                    myLsts.add(lst_3);
                }
            }
            else
            {
                myLsts.add(lst);
            }
        }
        return myLsts;
    }

    public String getMySql(List<List<String>> myLst)
    {
        StringBuilder sb = new StringBuilder();
        for (List<String> row : myLst)
        {
            for (String line : row)
            {
                sb.append(line + " ");
            }
        }

        return sb.toString();
    }

    private boolean isStream(String sql)
    {
        String pattern = "^SET\\s+STREAMING\\s+";
        Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
        Matcher m = p.matcher(sql.trim());
        return m.find();
    }

    public String myExecuteQuery(String sql) throws SQLException {
        if (!Strings.isNullOrEmpty(this.conn.getUserToken())) {

            if (isStream(sql))
            {
                return sql;
            }

            // 如果 mylst 中包括：loadFromNative 函数，则先执行这个函数来提交给服务器

            //String mysql0 = String.format("select smartSql(%s, ?)", this.conn.getGroup_id());
            String mysql0 = "select smartSql(?,?)";
            //String mysql0 = "select my_line_inary(?)";
            List<Object> lst = new ArrayList<Object>();
            lst.add(MyLineToBinary.objToBytes(this.conn.getUserToken()));
            if (this.mySqlAst != null && this.myLoadScript != null) {
                List<List<String>> myLst = reList(mySqlAst.getSmartSegment(sql));
                lst.add(MyLineToBinary.objToBytes(myLst));
            }
            else
            {
                lst.add(MyLineToBinary.objToBytes(sql));
            }

            //lst.add(sql);
            execute0(JdbcStatementType.SELECT_STATEMENT_TYPE, mysql0, lst);

            ResultSet rs = getResultSet();

            if (rs == null)
                throw new SQLException("The query isn't SELECT query: " + sql, SqlStateCode.PARSING_EXCEPTION);

            String mysql = "";
            while (rs.next()) {
                mysql = rs.getString(1);
            }
            rs.close();

            if (!Strings.isNullOrEmpty(mysql))
            {
                return mysql;
            }

            return sql;
        }
        return null;
    }

    /**
     * Creates new statement.
     *
     * @param conn JDBC connection.
     * @param resHoldability Result set holdability.
     * @param schema Schema name.
     */
    JdbcThinStatement(JdbcThinConnection conn, int resHoldability, String schema) {
        assert conn != null;

        this.conn = conn;
        this.resHoldability = resHoldability;
        this.schema = schema;

        HostAndPortRange[] hostAndPortRanges = conn.getConnProps().getAddresses();
        if (hostAndPortRanges.length > 0) {
            HostAndPortRange hostAndPortRange = hostAndPortRanges[0];
            //String host = hostAndPortRange.host();
            String connStr = hostAndPortRange.toString();
            ClientConfiguration cfg = new ClientConfiguration().setAddresses(connStr);
            this.client = Ignition.startClient(cfg);
        }
    }

    /** {@inheritDoc} */
    @Override public ResultSet executeQuery(String sql0) throws SQLException {
        String sql = "";
        if (!conn.isStream())
        {
            sql = myExecuteQuery(sql0);
        }
        else
        {
            sql = sql0;
        }
        execute0(JdbcStatementType.SELECT_STATEMENT_TYPE, sql, null);

        ResultSet rs = getResultSet();

        if (rs == null)
            throw new SQLException("The query isn't SELECT query: " + sql, SqlStateCode.PARSING_EXCEPTION);

        return rs;
    }

    /**
     * @param sql Query.
     * @return Native {@link SqlCommand}, or {@code null} if parsing was not successful.
     */
    private SqlCommand tryParseNative(String sql) {
        try {
            return new SqlParser(schema, sql).nextCommand();
        }
        catch (SqlParseException e) {
            return null;
        }
    }

    /**
     * @param sql Query.
     * @return Whether it's worth trying to parse this statement on the client.
     */
    private static boolean isEligibleForNativeParsing(String sql) {
        if (F.isEmpty(sql))
            return false;

        sql = sql.toUpperCase();

        int setPos = sql.indexOf(SqlKeyword.SET);

        if (setPos == -1)
            return false;

        int streamingPos = sql.indexOf(SqlKeyword.STREAMING);

        // There must be at least one symbol between SET and STREAMING.
        return streamingPos - setPos - SqlKeyword.SET.length() >= 1;
    }

    /**
     * @param stmtType Expected statement type.
     * @param sql Sql query.
     * @param args Query parameters.
     *
     * @throws SQLException Onj error.
     */
    protected void execute0(JdbcStatementType stmtType, String sql, List<Object> args) throws SQLException {
        ensureNotClosed();

        closeResults();

        if (sql == null || sql.isEmpty())
            throw new SQLException("SQL query is empty.");

        checkStatementBatchEmpty();

        SqlCommand nativeCmd = null;

        if (stmtType != JdbcStatementType.SELECT_STATEMENT_TYPE && isEligibleForNativeParsing(sql))
            nativeCmd = tryParseNative(sql);

        if (nativeCmd != null) {
            conn.executeNative(sql, nativeCmd, this);

            resultSets = Collections.singletonList(resultSetForUpdate(0));

            // If this command should be executed as native one, we do not treat it
            // as an ordinary batch citizen.
            return;
        }

        if (conn.isStream()) {
            if (stmtType == JdbcStatementType.SELECT_STATEMENT_TYPE)
                throw new SQLException("executeQuery() method is not allowed in streaming mode.",
                    SqlStateCode.INTERNAL_ERROR,
                    IgniteQueryErrorCode.UNSUPPORTED_OPERATION);

            conn.addBatch(sql, args);

            resultSets = Collections.singletonList(resultSetForUpdate(0));

            return;
        }

        JdbcQueryExecuteRequest req = new JdbcQueryExecuteRequest(
            stmtType,
            schema,
            this.conn.getUserToken(),
            pageSize,
            maxRows,
            conn.getAutoCommit(),
            explicitTimeout,
            sql,
            args == null ? null : args.toArray(new Object[args.size()])
        );

        JdbcResultWithIo resWithIo = conn.sendRequest(req, this, null);

        JdbcResult res0 = resWithIo.response();

        JdbcThinTcpIo stickyIo = resWithIo.cliIo();

        assert res0 != null;

        if (res0 instanceof JdbcBulkLoadAckResult)
            res0 = sendFile((JdbcBulkLoadAckResult)res0, stickyIo);

        if (res0 instanceof JdbcQueryExecuteResult) {
            JdbcQueryExecuteResult res = (JdbcQueryExecuteResult)res0;

            resultSets = Collections.singletonList(new JdbcThinResultSet(this, res.cursorId(), pageSize,
                res.last(), res.items(), res.isQuery(), conn.autoCloseServerCursor(), res.updateCount(),
                closeOnCompletion, stickyIo));
        }
        else if (res0 instanceof JdbcQueryExecuteMultipleStatementsResult) {
            JdbcQueryExecuteMultipleStatementsResult res = (JdbcQueryExecuteMultipleStatementsResult)res0;

            List<JdbcResultInfo> resInfos = res.results();

            resultSets = new ArrayList<>(resInfos.size());

            boolean firstRes = true;

            for (JdbcResultInfo rsInfo : resInfos) {
                if (!rsInfo.isQuery())
                    resultSets.add(resultSetForUpdate(rsInfo.updateCount()));
                else {
                    if (firstRes) {
                        firstRes = false;

                        resultSets.add(new JdbcThinResultSet(this, rsInfo.cursorId(), pageSize, res.isLast(),
                            res.items(), true, conn.autoCloseServerCursor(), -1, closeOnCompletion,
                            stickyIo));
                    }
                    else {
                        resultSets.add(new JdbcThinResultSet(this, rsInfo.cursorId(), pageSize, false,
                            null, true, conn.autoCloseServerCursor(), -1, closeOnCompletion,
                            stickyIo));
                    }
                }
            }
        }
        else
            throw new SQLException("Unexpected result [res=" + res0 + ']');

        assert !resultSets.isEmpty() : "At least one results set is expected";
    }

    /**
     * Check that user has not added anything to this statement's batch prior to turning streaming on.
     * @throws SQLException if failed.
     */
    void checkStatementBatchEmpty() throws SQLException {
        if (conn.isStream() && !F.isEmpty(batch))
            throw new IgniteSQLException("Statement has non-empty batch (call executeBatch() or clearBatch() " +
                "before enabling streaming).", IgniteQueryErrorCode.UNSUPPORTED_OPERATION).toJdbcException();
    }

    /**
     * @param cnt Update counter.
     * @return Result set for given update counter.
     */
    private JdbcThinResultSet resultSetForUpdate(long cnt) {
        return new JdbcThinResultSet(this, -1, pageSize,
            true, Collections.<List<Object>>emptyList(), false,
            conn.autoCloseServerCursor(), cnt, closeOnCompletion, null);
    }

    /**
     * Sends a file to server in batches via multiple {@link JdbcBulkLoadBatchRequest}s.
     *
     * @param cmdRes Result of invoking COPY command: contains server-parsed
     *    bulk load parameters, such as file name and batch size.
     * @return Bulk load result.
     * @throws SQLException On error.
     */
    private JdbcResult sendFile(JdbcBulkLoadAckResult cmdRes, JdbcThinTcpIo stickyIO) throws SQLException {
        String fileName = cmdRes.params().localFileName();
        int batchSize = cmdRes.params().packetSize();

        int batchNum = 0;

        try {
            try (InputStream input = new BufferedInputStream(new FileInputStream(fileName))) {
                byte[] buf = new byte[batchSize];

                int readBytes;
                int timeSpendMillis = 0;

                while ((readBytes = input.read(buf)) != -1) {
                    long startTime = System.currentTimeMillis();

                    if (readBytes == 0)
                        continue;

                    if (reqTimeout != JdbcThinConnection.NO_TIMEOUT)
                        reqTimeout -= timeSpendMillis;

                    JdbcResult res = conn.sendRequest(new JdbcBulkLoadBatchRequest(
                            cmdRes.cursorId(),
                            batchNum++,
                            JdbcBulkLoadBatchRequest.CMD_CONTINUE,
                            readBytes == buf.length ? buf : Arrays.copyOf(buf, readBytes)),
                        this, stickyIO).response();

                    if (!(res instanceof JdbcQueryExecuteResult))
                        throw new SQLException("Unknown response sent by the server: " + res);

                    timeSpendMillis = (int)(System.currentTimeMillis() - startTime);
                }

                if (reqTimeout != JdbcThinConnection.NO_TIMEOUT)
                    reqTimeout -= timeSpendMillis;

                return conn.sendRequest(new JdbcBulkLoadBatchRequest(
                        cmdRes.cursorId(),
                        batchNum++,
                        JdbcBulkLoadBatchRequest.CMD_FINISHED_EOF),
                    this, stickyIO).response();
            }
        }
        catch (Exception e) {
            if (e instanceof SQLTimeoutException)
                throw (SQLTimeoutException)e;

            try {
                conn.sendRequest(new JdbcBulkLoadBatchRequest(
                        cmdRes.cursorId(),
                        batchNum,
                        JdbcBulkLoadBatchRequest.CMD_FINISHED_ERROR),
                    this, stickyIO);
            }
            catch (SQLException e1) {
                throw new SQLException("Cannot send finalization request: " + e1.getMessage(), e);
            }

            if (e instanceof SQLException)
                throw (SQLException) e;
            else
                throw new SQLException("Failed to read file: '" + fileName + "'", SqlStateCode.INTERNAL_ERROR, e);
        }
    }

    /** {@inheritDoc} */
    @Override public int executeUpdate(String sql0) throws SQLException {
        String sql = "";
        if (!conn.isStream())
        {
            sql = myExecuteQuery(sql0);
        }
        else
        {
            sql = sql0;
        }
        execute0(JdbcStatementType.UPDATE_STMT_TYPE, sql, null);

        int res = getUpdateCount();

        if (res == -1)
            throw new SQLException("The query is not DML statememt: " + sql);

        return res;
//        boolean f = this.execute(sql);
//        if (f == true)
//        {
//            return 0;
//        }
//        return -1;
    }

    /** {@inheritDoc} */
    @Override public void close() throws SQLException {
        if (isClosed())
            return;

        try {
            closeResults();

            conn.closeStatement(this);
        }
        finally {
            closed = true;
        }
    }

    /**
     * Close results.
     * @throws SQLException On error.
     */
    private void closeResults() throws SQLException {
        if (resultSets != null) {
            for (JdbcThinResultSet rs : resultSets)
                rs.close0();

            resultSets = null;
            curRes = 0;
        }

        synchronized (cancellationMux) {
            currReqId = 0;

            stickyIo = null;

            cancelled = false;
        }
    }

    /**
     * @return Returns true if statement was cancelled, false otherwise.
     */
    boolean isCancelled() {
        return cancelled;
    }

    /**
     *
     */
    void closeOnDisconnect() {
        if (resultSets != null) {
            for (JdbcThinResultSet rs : resultSets)
                rs.closeOnDisconnect();

            resultSets = null;
        }

        closed = true;
    }

    /** {@inheritDoc} */
    @Override public int getMaxFieldSize() throws SQLException {
        ensureNotClosed();

        return 0;
    }

    /** {@inheritDoc} */
    @Override public void setMaxFieldSize(int max) throws SQLException {
        ensureNotClosed();

        if (max < 0)
            throw new SQLException("Invalid field limit.");

        throw new SQLFeatureNotSupportedException("Field size limitation is not supported.");
    }

    /** {@inheritDoc} */
    @Override public int getMaxRows() throws SQLException {
        ensureNotClosed();

        return maxRows;
    }

    /** {@inheritDoc} */
    @Override public void setMaxRows(int maxRows) throws SQLException {
        ensureNotClosed();

        if (maxRows < 0)
            throw new SQLException("Invalid max rows value.");

        this.maxRows = maxRows;
    }

    /** {@inheritDoc} */
    @Override public void setEscapeProcessing(boolean enable) throws SQLException {
        ensureNotClosed();
    }

    /** {@inheritDoc} */
    @Override public int getQueryTimeout() throws SQLException {
        ensureNotClosed();

        return timeout / 1000;
    }

    /** {@inheritDoc} */
    @Override public void setQueryTimeout(int timeout) throws SQLException {
        ensureNotClosed();

        if (timeout < 0)
            throw new SQLException("Invalid timeout value.");

        timeout(timeout * 1000 > timeout ? timeout * 1000 : Integer.MAX_VALUE);
    }

    /** {@inheritDoc} */
    @Override public void cancel() throws SQLException {
        ensureNotClosed();

        if (!isQueryCancellationSupported())
            throw new SQLFeatureNotSupportedException("Cancel method is not supported.");

        long reqId;

        JdbcThinTcpIo cliIo;

        synchronized (cancellationMux) {
            if (isCancelled())
                return;

            if (conn.isStream())
                throw new SQLFeatureNotSupportedException("Cancel method is not allowed in streaming mode.");

            reqId = currReqId;

            if (reqId != 0)
                cancelled = true;

            cliIo = stickyIo;
        }

        if (reqId != 0)
            conn.sendQueryCancelRequest(new JdbcQueryCancelRequest(reqId), cliIo);
    }

    /** {@inheritDoc} */
    @Override public SQLWarning getWarnings() throws SQLException {
        ensureNotClosed();

        return null;
    }

    /** {@inheritDoc} */
    @Override public void clearWarnings() throws SQLException {
        ensureNotClosed();
    }

    /** {@inheritDoc} */
    @Override public void setCursorName(String name) throws SQLException {
        ensureNotClosed();

        throw new SQLFeatureNotSupportedException("Updates are not supported.");
    }

    /** {@inheritDoc} */
    @Override public boolean execute(String sql0) throws SQLException {
        String sql = "";
        if (!conn.isStream())
        {
            sql = myExecuteQuery(sql0);
        }
        else
        {
            sql = sql0;
        }
        ensureNotClosed();

        execute0(JdbcStatementType.ANY_STATEMENT_TYPE, sql, null);

        return resultSets.get(0).isQuery();
    }

    /** {@inheritDoc} */
    @Override public ResultSet getResultSet() throws SQLException {
        ensureAlive();

        if (resultSets == null || curRes >= resultSets.size())
            return null;

        JdbcThinResultSet rs = resultSets.get(curRes);

        if (!rs.isQuery())
            return null;

        return rs;
    }

    /** {@inheritDoc} */
    @Override public int getUpdateCount() throws SQLException {
        ensureAlive();

        if (resultSets == null || curRes >= resultSets.size())
            return -1;

        JdbcThinResultSet rs = resultSets.get(curRes);

        if (rs.isQuery())
            return -1;

        return (int)rs.updatedCount();
    }

    /** {@inheritDoc} */
    @Override public boolean getMoreResults() throws SQLException {
        return getMoreResults(CLOSE_CURRENT_RESULT);
    }

    /** {@inheritDoc} */
    @Override public void setFetchDirection(int direction) throws SQLException {
        ensureNotClosed();

        if (direction != FETCH_FORWARD)
            throw new SQLFeatureNotSupportedException("Only forward direction is supported.");
    }

    /** {@inheritDoc} */
    @Override public int getFetchDirection() throws SQLException {
        ensureNotClosed();

        return FETCH_FORWARD;
    }

    /** {@inheritDoc} */
    @Override public void setFetchSize(int fetchSize) throws SQLException {
        ensureNotClosed();

        if (fetchSize <= 0)
            throw new SQLException("Fetch size must be greater than zero.");

        pageSize = fetchSize;
    }

    /** {@inheritDoc} */
    @Override public int getFetchSize() throws SQLException {
        ensureNotClosed();

        return pageSize;
    }

    /** {@inheritDoc} */
    @Override public int getResultSetConcurrency() throws SQLException {
        ensureNotClosed();

        return CONCUR_READ_ONLY;
    }

    /** {@inheritDoc} */
    @Override public int getResultSetType() throws SQLException {
        ensureNotClosed();

        return TYPE_FORWARD_ONLY;
    }

    /** {@inheritDoc} */
    @Override public void addBatch(String sql0) throws SQLException {
        String sql = "";
        if (!conn.isStream())
        {
            sql = myExecuteQuery(sql0);
        }
        else
        {
            sql = sql0;
        }
        ensureNotClosed();

        checkStatementEligibleForBatching(sql);

        checkStatementBatchEmpty();

        batchSize++;

        if (conn.isStream()) {
            conn.addBatch(sql, null);

            return;
        }

        if (batch == null)
            batch = new ArrayList<>();

        batch.add(new JdbcQuery(sql, null));
    }

    /**
     * Check that we're not trying to add to connection's batch a native command (it should be executed explicitly).
     * @param sql SQL command.
     * @throws SQLException if there's an attempt to add a native command to JDBC batch.
     */
    void checkStatementEligibleForBatching(String sql) throws SQLException {
        SqlCommand nativeCmd = null;

        if (isEligibleForNativeParsing(sql))
            nativeCmd = tryParseNative(sql);

        if (nativeCmd != null) {
            assert nativeCmd instanceof SqlSetStreamingCommand;

            throw new SQLException("Streaming control commands must be executed explicitly - " +
                "either via Statement.execute(String), or via using prepared statements.",
                SqlStateCode.UNSUPPORTED_OPERATION);
        }
    }

    /** {@inheritDoc} */
    @Override public void clearBatch() throws SQLException {
        ensureNotClosed();

        batchSize = 0;

        batch = null;
    }

    /** {@inheritDoc} */
    @Override public int[] executeBatch() throws SQLException {
        ensureNotClosed();

        closeResults();

        checkStatementBatchEmpty();

        if (conn.isStream()) {
            int[] res = new int[batchSize];

            batchSize = 0;

            return res;
        }

        if (F.isEmpty(batch))
            return new int[0];

        JdbcBatchExecuteRequest req = new JdbcBatchExecuteRequest(conn.getSchema(), conn.getUserToken(), batch,
            conn.getAutoCommit(), false);

        try {
            JdbcBatchExecuteResult res = conn.sendRequest(req, this, null).response();

            if (res.errorCode() != ClientListenerResponse.STATUS_SUCCESS) {
                throw new BatchUpdateException(res.errorMessage(), IgniteQueryErrorCode.codeToSqlState(res.errorCode()),
                    res.errorCode(), res.updateCounts());
            }

            return res.updateCounts();
        }
        finally {
            batchSize = 0;

            batch = null;
        }
    }

    /** {@inheritDoc} */
    @Override public Connection getConnection() throws SQLException {
        ensureNotClosed();

        return conn;
    }

    /** {@inheritDoc} */
    @Override public boolean getMoreResults(int curr) throws SQLException {
        ensureAlive();

        if (resultSets == null || curRes >= resultSets.size())
            return false;

        curRes++;

        if (resultSets != null) {
            assert curRes <= resultSets.size() : "Invalid results state: [resultsCount=" + resultSets.size() +
                ", curRes=" + curRes + ']';

            switch (curr) {
                case CLOSE_CURRENT_RESULT:
                    if (curRes > 0)
                        resultSets.get(curRes - 1).close0();

                    break;

                case CLOSE_ALL_RESULTS:
                    for (int i = 0; i < curRes; ++i)
                        resultSets.get(i).close0();

                    break;

                case KEEP_CURRENT_RESULT:
                    break;

                default:
                    throw new SQLException("Invalid 'current' parameter.");
            }
        }

        return (resultSets != null && curRes < resultSets.size());
    }

    /** {@inheritDoc} */
    @Override public ResultSet getGeneratedKeys() throws SQLException {
        ensureAlive();

        throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");
    }

    /** {@inheritDoc} */
    @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        ensureNotClosed();

        switch (autoGeneratedKeys) {
            case Statement.RETURN_GENERATED_KEYS:
                throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");

            case Statement.NO_GENERATED_KEYS:
                return executeUpdate(sql);

            default:
                throw new SQLException("Invalid autoGeneratedKeys value");
        }
    }

    /** {@inheritDoc} */
    @Override public int executeUpdate(String sql, int[] colIndexes) throws SQLException {
        ensureNotClosed();

        throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");
    }

    /** {@inheritDoc} */
    @Override public int executeUpdate(String sql, String[] colNames) throws SQLException {
        ensureNotClosed();

        throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");
    }

    /** {@inheritDoc} */
    @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        ensureNotClosed();

        switch (autoGeneratedKeys) {
            case Statement.RETURN_GENERATED_KEYS:
                throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");

            case Statement.NO_GENERATED_KEYS:
                return execute(sql);

            default:
                throw new SQLException("Invalid autoGeneratedKeys value.");
        }
    }

    /** {@inheritDoc} */
    @Override public boolean execute(String sql, int[] colIndexes) throws SQLException {
        ensureNotClosed();

        if (colIndexes != null && colIndexes.length > 0)
            throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");

        return execute(sql);
    }

    /** {@inheritDoc} */
    @Override public boolean execute(String sql, String[] colNames) throws SQLException {
        ensureNotClosed();

        if (colNames != null && colNames.length > 0)
            throw new SQLFeatureNotSupportedException("Auto-generated columns are not supported.");

        return execute(sql);
    }

    /** {@inheritDoc} */
    @Override public int getResultSetHoldability() throws SQLException {
        ensureNotClosed();

        return resHoldability;
    }

    /** {@inheritDoc} */
    @Override public boolean isClosed() throws SQLException {
        return conn.isClosed() || closed;
    }

    /** {@inheritDoc} */
    @Override public void setPoolable(boolean poolable) throws SQLException {
        ensureNotClosed();

        if (poolable)
            throw new SQLFeatureNotSupportedException("Pooling is not supported.");
    }

    /** {@inheritDoc} */
    @Override public boolean isPoolable() throws SQLException {
        ensureNotClosed();

        return false;
    }

    /** {@inheritDoc} */
    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        if (!isWrapperFor(iface))
            throw new SQLException("Statement is not a wrapper for " + iface.getName());

        return (T)this;
    }

    /** {@inheritDoc} */
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface != null && iface.isAssignableFrom(JdbcThinStatement.class);
    }

    /** {@inheritDoc} */
    @Override public void closeOnCompletion() throws SQLException {
        ensureNotClosed();

        closeOnCompletion = true;

        if (resultSets != null) {
            for (JdbcThinResultSet rs : resultSets)
                rs.closeStatement(true);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean isCloseOnCompletion() throws SQLException {
        ensureNotClosed();

        return closeOnCompletion;
    }

    /**
     * Sets timeout in milliseconds.
     *
     * For test purposes.
     *
     * @param timeout Timeout.
     */
    public final void timeout(int timeout) {
        assert timeout >= 0;

        this.timeout = timeout;

        reqTimeout = this.timeout;

        explicitTimeout = true;
    }

    /**
     * @return Connection.
     */
    JdbcThinConnection connection() {
        return conn;
    }

    /**
     * Ensures that statement not closed.
     *
     * @throws SQLException If statement is closed.
     */
    void ensureNotClosed() throws SQLException {
        if (isClosed())
            throw new SQLException("Statement is closed.");
    }

    /**
     * Ensures that statement neither closed nor canceled.
     *
     * @throws SQLException If statement is closed or canceled.
     */
    void ensureAlive() throws SQLException {
        ensureNotClosed();

        if (cancelled)
            throw new SQLException("The query was cancelled while executing.", SqlStateCode.QUERY_CANCELLED);
    }

    /**
     * Used by statement on closeOnCompletion mode.
     * @throws SQLException On error.
     */
    void closeIfAllResultsClosed() throws SQLException {
        if (isClosed())
            return;

        boolean allRsClosed = true;

        if (resultSets != null) {
            for (JdbcThinResultSet rs : resultSets) {
                if (!rs.isClosed())
                    allRsClosed = false;
            }
        }

        if (allRsClosed)
            close();
    }

    /**
     * Sets current request id and sticky IO.
     *
     * @param currReqId Current request Id.
     * @param currCliIo Current ignite endpoint IO.
     */
    void currentRequestMeta(long currReqId, JdbcThinTcpIo currCliIo) {
        assert Thread.holdsLock(cancellationMux);

        this.currReqId = currReqId;
        stickyIo = currCliIo;
    }

    /**
     * @return Current request Id.
     */
    long currentRequestId() {
        synchronized (cancellationMux) {
            return currReqId;
        }
    }

    /**
     * @return Cancellation mutex.
     */
    Object cancellationMutex() {
        return cancellationMux;
    }

    /**
     * @return True if query cancellation supported, false otherwise.
     */
    private boolean isQueryCancellationSupported() {
        return conn.isQueryCancellationSupported();
    }

    /**
     * @return Request timeout.
     */
    int requestTimeout() {
        return reqTimeout;
    }
}
