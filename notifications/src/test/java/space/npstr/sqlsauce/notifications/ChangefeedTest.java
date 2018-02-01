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

package space.npstr.sqlsauce.notifications;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import space.npstr.sqlsauce.BaseTest;
import space.npstr.sqlsauce.DatabaseWrapper;
import space.npstr.sqlsauce.DbUtils;
import space.npstr.sqlsauce.notifications.changefeed.ChangeFeedAdapter;
import space.npstr.sqlsauce.notifications.changefeed.ChangeFeedNotification;
import space.npstr.sqlsauce.notifications.changefeed.DeleteNotification;
import space.npstr.sqlsauce.notifications.changefeed.InsertNotification;
import space.npstr.sqlsauce.notifications.changefeed.UpdateNotification;
import space.npstr.sqlsauce.notifications.exceptions.SimpleNsExceptionHandler;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by napster on 01.02.18.
 */
public class ChangefeedTest extends BaseTest {

    private final String channel = "changefeed_test_changefeed";
    private final String schemaName = "public";
    private final String tableName = "changefeed_test";
    private final String schemaTable = schemaName + "." + tableName;


    private final String idColumn = "id";
    private final int insertId = 42;
    private final String nameColumn = "name";
    private final String insertName = "Gazorpazorp";
    private final String updateName = "Gearhead";


    @Test
    void insertTest() throws IOException, InterruptedException {
        int interval = 100;


        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        List<InsertNotification> insertNotifications = new ArrayList<>();
        List<UpdateNotification> updateNotifications = new ArrayList<>();
        List<DeleteNotification> deleteNotifications = new ArrayList<>();

        NotificationService ns = new NotificationService(getTestJdbcUrl(), NotifyTest.class.getSimpleName(),
                interval, (SimpleNsExceptionHandler) exceptions::add);
        ns.addListener(new ChangeFeedAdapter() {
            @Override
            public void onInsert(InsertNotification insertNotification) {
                insertNotifications.add(insertNotification);
            }

            @Override
            public void onUpdate(UpdateNotification updateNotification) {
                updateNotifications.add(updateNotification);
            }

            @Override
            public void onDelete(DeleteNotification deleteNotification) {
                deleteNotifications.add(deleteNotification);
            }
        }, channel);

        DatabaseWrapper wrapper = new DatabaseWrapper(requireConnection());
        URL resource = ChangefeedTest.class.getClassLoader().getResource("watch.sql");
        assertNotNull(resource, "Couldnt find watch.sql file");
        String initWatch = new String(Files.readAllBytes(Paths.get(resource.getPath())), Charset.defaultCharset());

        //prime the db
        wrapper.executeSqlQuery(initWatch, null);

        //prepare a table
        wrapper.executeSqlQuery(String.format(DROP_TABLE_IF_EXISTS, schemaTable), null);
        wrapper.executeSqlQuery(String.format(CREATE_SIMPLE_TABLE, schemaTable), null);

        //watch the table
        Map<String, Object> params = DbUtils.paramsOf(
                "table_name", schemaTable,
                "channel", channel
        );
        //language=PostgreSQL
        String watchSql = "SELECT cast(watch_table(cast(:table_name AS REGCLASS), :channel) AS TEXT);";
        List<Object> ignored = wrapper.selectSqlQuery(watchSql, params);
        if (ignored.isEmpty()) {//todo implement native function calling properly
            //do nothing, this check only exists cause spotbugs is fucking retarded (and I'm a retard for insisting on running it on the test classes)
        }


        params = DbUtils.paramsOf(
                idColumn, insertId,
                nameColumn, insertName
        );
        Thread.sleep(interval); //make sure listener is set up
        //language=PostgreSQL
        wrapper.executeSqlQuery(String.format("INSERT INTO %s (%s, %s) VALUES (:id, :name);", schemaTable, idColumn, nameColumn), params);
        params.put(nameColumn, updateName);
        //language=PostgreSQL
        wrapper.executeSqlQuery(String.format("UPDATE %s SET (%s, %s) = (:id, :name)", schemaTable, idColumn, nameColumn), params);
        //language=PostgreSQL
        wrapper.executeSqlQuery(String.format("DELETE FROM %s WHERE %s = :id AND %s = :name", schemaTable, idColumn, nameColumn), params);
        Thread.sleep(interval); //make sure notifications are fetched

        for (Exception e : exceptions) {
            log.error("NotificationService threw exception", e);
        }
        assertEquals(0, exceptions.size(), "NotificationService threw exceptions");

        //insert notification
        assertTrue(insertNotifications.size() > 0, "Did not receive insert notification");
        assertTrue(insertNotifications.size() < 2, "Received more than 1 insert notification");
        checkInsertNotification(insertNotifications.get(0));

        //update notification
        assertTrue(updateNotifications.size() > 0, "Did not receive update notification");
        assertTrue(updateNotifications.size() < 2, "Received more than 1 update notification");
        checkUpdateNotification(updateNotifications.get(0));

        //delete notification
        assertTrue(deleteNotifications.size() > 0, "Did not receive delete notification");
        assertTrue(deleteNotifications.size() < 2, "Received more than 1 delete notification");
        checkDeleteNotification(deleteNotifications.get(0));
    }

    private void checkInsertNotification(InsertNotification insertNotification) {
        assertNotNull(insertNotification, "Insert notification is null");

        assertNotNull(insertNotification.getSource(), "Insert source notification is null");
        assertNotNull(insertNotification.getChannel(), "Insert channel is null");
        assertEquals(channel, insertNotification.getChannel(), "Insert notification received on wrong channel");
        assertNotNull(insertNotification.getRawPayload(), "Insert payload is null");

        assertNotNull(insertNotification.getSchemaName(), "Insert schema name is null");
        assertEquals(schemaName, insertNotification.getSchemaName(), "Insert schema name is wrong");

        assertNotNull(insertNotification.getTableName(), "Insert table name is null");
        assertEquals(tableName, insertNotification.getTableName(), "Insert table name is wrong");

        assertNotNull(insertNotification.getOperation(), "Insert operation is null");
        assertEquals(ChangeFeedNotification.Operation.INSERT, insertNotification.getOperation(), "Insert operation is wrong");

        assertNotNull(insertNotification.getTransactionTime(), "Insert transaction time is null");
        assertNotNull(insertNotification.getCaptureTime(), "Insert capture time is null");
        //todo plausibility checks on the times?

        JSONObject rowData = insertNotification.getRowdataNew();
        assertNotNull(rowData, "Insert row data is null");
        assertEquals(insertId, rowData.getInt(idColumn), "Insert row data id is wrong");
        assertEquals(insertName, rowData.getString(nameColumn), "Insert row data name is wrong");
    }

    private void checkUpdateNotification(UpdateNotification updateNotification) {
        assertNotNull(updateNotification, "Update notification is null");

        assertNotNull(updateNotification.getSource(), "Update source notification is null");
        assertNotNull(updateNotification.getChannel(), "Update channel is null");
        assertEquals(channel, updateNotification.getChannel(), "Update notification received on wrong channel");
        assertNotNull(updateNotification.getRawPayload(), "Update payload is null");

        assertNotNull(updateNotification.getSchemaName(), "Update schema name is null");
        assertEquals(schemaName, updateNotification.getSchemaName(), "Update schema name is wrong");

        assertNotNull(updateNotification.getTableName(), "Update table name is null");
        assertEquals(tableName, updateNotification.getTableName(), "Update table name is wrong");

        assertNotNull(updateNotification.getOperation(), "Update operation is null");
        assertEquals(ChangeFeedNotification.Operation.UPDATE, updateNotification.getOperation(), "Update operation is wrong");

        assertNotNull(updateNotification.getTransactionTime(), "Update transaction time is null");
        assertNotNull(updateNotification.getCaptureTime(), "Update capture time is null");
        //todo plausibility checks on the times?

        JSONObject rowDataOld = updateNotification.getRowdataOld();
        assertNotNull(rowDataOld, "Update row data old is null");
        assertEquals(insertId, rowDataOld.getInt(idColumn), "Update row data old id is wrong");
        assertEquals(insertName, rowDataOld.getString(nameColumn), "Update row data old name is wrong");

        JSONObject rowDataNew = updateNotification.getRowdataNew();
        assertNotNull(rowDataNew, "Update row data new is null");
        assertEquals(insertId, rowDataNew.getInt(idColumn), "Update row data new id is wrong");
        assertEquals(updateName, rowDataNew.getString(nameColumn), "Update row data new name is wrong");
    }

    private void checkDeleteNotification(DeleteNotification deleteNotification) {
        assertNotNull(deleteNotification, "Delete notification is null");

        assertNotNull(deleteNotification.getSource(), "Delete source notification is null");
        assertNotNull(deleteNotification.getChannel(), "Delete channel is null");
        assertEquals(channel, deleteNotification.getChannel(), "Delete notification received on wrong channel");
        assertNotNull(deleteNotification.getRawPayload(), "Delete payload is null");

        assertNotNull(deleteNotification.getSchemaName(), "Delete schema name is null");
        assertEquals(schemaName, deleteNotification.getSchemaName(), "Delete schema name is wrong");

        assertNotNull(deleteNotification.getTableName(), "Delete table name is null");
        assertEquals(tableName, deleteNotification.getTableName(), "Delete table name is wrong");

        assertNotNull(deleteNotification.getOperation(), "Delete operation is null");
        assertEquals(ChangeFeedNotification.Operation.DELETE, deleteNotification.getOperation(), "Delete operation is wrong");

        assertNotNull(deleteNotification.getTransactionTime(), "Delete transaction time is null");
        assertNotNull(deleteNotification.getCaptureTime(), "Delete capture time is null");
        //todo plausibility checks on the times?

        JSONObject rowData = deleteNotification.getRowdataOld();
        assertNotNull(rowData, "Delete row data is null");
        assertEquals(insertId, rowData.getInt(idColumn), "Delete row data id is wrong");
        assertEquals(updateName, rowData.getString(nameColumn), "Delete row data name is wrong");
    }
}
