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

package space.npstr.sqlsauce.notifications.exceptions;

import space.npstr.sqlsauce.notifications.listeners.NotificationListener;

/**
 * Created by napster on 01.02.18.
 * <p>
 * Exception handler for the NotificationService.
 * See {@link LoggingNsExceptionHandler} and {@link NoopNsExceptionHandler} for default implementations, or roll your own.
 */
public interface NsExceptionHandler {
    /**
     * Any exceptions, most notably all kinds of SQL exceptions will be passed in here.
     */
    void handleNotificationServiceException(Exception e);

    /**
     * Any uncaught exceptions from calling {@link NotificationListener#notif} will be passed in here.
     */
    void handleListenerException(Exception e);
}
