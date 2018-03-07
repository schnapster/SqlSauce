/*
 * MIT License
 *
 * Copyright (c) 2017-2018, Dennis Neufeld
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package space.npstr.sqlsauce.notifications.changefeed;

import org.json.JSONException;
import org.json.JSONObject;
import org.postgresql.PGNotification;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

/**
 * Created by napster on 01.02.18.
 */
public abstract class ChangeFeedNotification {

    private final PGNotification source;
    private final String schemaName;
    private final String tableName;
    private final Operation operation;
    private final OffsetDateTime transactionTime;
    private final OffsetDateTime captureTime;

    /**
     * @throws JSONException
     *         if the passed payload did not fit our expectations
     */
    @Nullable
    public static ChangeFeedNotification parse(PGNotification notification) throws JSONException {

        JSONObject asJsonObject = new JSONObject(notification.getParameter());
        int sqlsauceNotificationsVersion = asJsonObject.optInt("sqlsauce_notifications_version", 0);
        switch (sqlsauceNotificationsVersion) {
            case 1:
                return parseV1Notification(notification, asJsonObject);
            case 0:
            default:
                return null;
        }
    }

    private static ChangeFeedNotification parseV1Notification(PGNotification notification, JSONObject payload)
            throws JSONException {
        String schemaName = payload.getString("schema_name");
        String tableName = payload.getString("table_name");
        Operation operation = Operation.valueOf(payload.getString("operation"));
        OffsetDateTime transactionTime = OffsetDateTime.parse(payload.getString("transaction_time"));
        OffsetDateTime captureTime = OffsetDateTime.parse(payload.getString("capture_time"));
        JSONObject rowDataOld;
        JSONObject rowDataNew;

        switch (operation) {
            case INSERT:
                rowDataNew = payload.getJSONObject("rowdata_new");
                return new InsertNotification(notification, schemaName, tableName, operation, transactionTime,
                        captureTime, rowDataNew);
            case UPDATE:
                rowDataOld = payload.getJSONObject("rowdata_old");
                rowDataNew = payload.getJSONObject("rowdata_new");
                return new UpdateNotification(notification, schemaName, tableName, operation, transactionTime,
                        captureTime, rowDataOld, rowDataNew);
            case DELETE:
                rowDataOld = payload.getJSONObject("rowdata_old");
                return new DeleteNotification(notification, schemaName, tableName, operation, transactionTime,
                        captureTime, rowDataOld);
            default:
                throw new IllegalStateException("Unknown operation: " + operation);
        }
    }

    protected ChangeFeedNotification(PGNotification source, String schemaName, String tableName, Operation operation,
                                     OffsetDateTime transactionTime, OffsetDateTime captureTime) {
        this.source = source;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.operation = operation;
        this.transactionTime = transactionTime;
        this.captureTime = captureTime;
    }

    public String getChannel() {
        return source.getName();
    }

    public int getPID() {
        return source.getPID();
    }

    public String getRawPayload() {
        return source.getParameter();
    }

    public PGNotification getSource() {
        return source;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public Operation getOperation() {
        return operation;
    }

    public OffsetDateTime getTransactionTime() {
        return transactionTime;
    }

    public OffsetDateTime getCaptureTime() {
        return captureTime;
    }

    public enum Operation {
        INSERT,
        UPDATE,
        DELETE,
    }

}
