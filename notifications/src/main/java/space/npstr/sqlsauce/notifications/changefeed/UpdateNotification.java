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

import org.json.JSONObject;
import org.postgresql.PGNotification;

import java.time.OffsetDateTime;

/**
 * Created by napster on 01.02.18.
 */
public class UpdateNotification extends ChangeFeedNotification {

    private final JSONObject rowdataOld;
    private final JSONObject rowdataNew;

    protected UpdateNotification(PGNotification source, String schemaName, String tableName, Operation operation,
                                 OffsetDateTime transactionTime, OffsetDateTime captureTime, JSONObject rowdataOld,
                                 JSONObject rowdataNew) {
        super(source, schemaName, tableName, operation, transactionTime, captureTime);
        this.rowdataOld = rowdataOld;
        this.rowdataNew = rowdataNew;
    }

    public JSONObject getRowdataOld() {
        return rowdataOld;
    }

    public JSONObject getRowdataNew() {
        return rowdataNew;
    }
}
