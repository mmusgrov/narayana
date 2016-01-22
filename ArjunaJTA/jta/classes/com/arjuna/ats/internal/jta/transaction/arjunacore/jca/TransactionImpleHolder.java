/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.arjuna.ats.internal.jta.transaction.arjunacore.jca;

import com.arjuna.ats.internal.jta.transaction.arjunacore.subordinate.jca.TransactionImple;

import java.util.concurrent.CountDownLatch;

public class TransactionImpleHolder {
    private TransactionImple imported;
    private CountDownLatch latch = new CountDownLatch(1);

    public TransactionImple getImported() throws InterruptedException {
        latch.await();

        return imported;
    }

    public void setImported(TransactionImple imported) {
        this.imported = imported;
        latch.countDown();
    }
}
