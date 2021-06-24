/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.narayana.jta.jms;

import com.arjuna.ats.jta.logging.jtaLogger;

import javax.jms.BytesMessage;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.XAJMSContext;
import javax.transaction.Synchronization;
import javax.transaction.xa.XAResource;
import java.io.Serializable;

public class ContextProxy implements JMSContext {
    private final JMSContext jmsContext;

    private final TransactionHelper transactionHelper;

    /**
     * @param jmsContext XA context that needs to be proxied.
     * @param transactionHelper utility to make transaction resources registration easier.
     */
    public ContextProxy(XAJMSContext jmsContext, TransactionHelper transactionHelper) {
        this.jmsContext = jmsContext;
        this.transactionHelper = transactionHelper;
    }

    @Override
    public void close() {
        try {
            if (jmsContext instanceof XAJMSContext && transactionHelper.isTransactionAvailable()) {
                XAResource xaResource = ((XAJMSContext) jmsContext).getXAResource();

                if (xaResource != null) {
                    transactionHelper.deregisterXAResource(xaResource);

                    if (jtaLogger.logger.isTraceEnabled()) {
                        jtaLogger.logger.trace("Delisted " + xaResource + " XA resource from the transaction");
                    }

                    Synchronization synchronization = new ContextClosingSynchronization(this);

                    transactionHelper.registerSynchronization(synchronization);
                }
            } else {
                jmsContext.close();
            }
        } catch (JMSException e) {
            if (jtaLogger.logger.isTraceEnabled()) {
                jtaLogger.logger.trace("Unable to lookup or degregister context from current context: " + e.getMessage());
            }

            jmsContext.close();
        }
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return jmsContext.createContext(sessionMode);
    }

    @Override
    public JMSProducer createProducer() {
        return jmsContext.createProducer();
    }

    @Override
    public String getClientID() {
        return jmsContext.getClientID();
    }

    @Override
    public void setClientID(String clientID) {
        jmsContext.setClientID(clientID);
    }

    @Override
    public ConnectionMetaData getMetaData() {
        return jmsContext.getMetaData();
    }

    @Override
    public ExceptionListener getExceptionListener() {
        return jmsContext.getExceptionListener();
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) {
        jmsContext.setExceptionListener(listener);
    }

    @Override
    public void start() {
        jmsContext.start();
    }

    @Override
    public void stop() {
        jmsContext.stop();
    }

    @Override
    public void setAutoStart(boolean autoStart) {
        jmsContext.setAutoStart(autoStart);
    }

    @Override
    public boolean getAutoStart() {
        return jmsContext.getAutoStart();
    }

    @Override
    public BytesMessage createBytesMessage() {
        return jmsContext.createBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() {
        return jmsContext.createMapMessage();
    }

    @Override
    public Message createMessage() {
        return jmsContext.createMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() {
        return jmsContext.createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) {
        return jmsContext.createObjectMessage(object);
    }

    @Override
    public StreamMessage createStreamMessage() {
        return jmsContext.createStreamMessage();
    }

    @Override
    public TextMessage createTextMessage() {
        return jmsContext.createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) {
        return jmsContext.createTextMessage(text);
    }

    @Override
    public boolean getTransacted() {
        return jmsContext.getTransacted();
    }

    @Override
    public int getSessionMode() {
        return jmsContext.getSessionMode();
    }

    @Override
    public void commit() {
        jmsContext.commit();
    }

    @Override
    public void rollback() {
        jmsContext.rollback();
    }

    @Override
    public void recover() {
        jmsContext.recover();
    }

    @Override
    public JMSConsumer createConsumer(Destination destination) {
        return jmsContext.createConsumer(destination);
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String messageSelector) {
        return jmsContext.createConsumer(destination, messageSelector);
    }

    @Override
    public JMSConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
        return jmsContext.createConsumer(destination, messageSelector, noLocal);
    }

    @Override
    public Queue createQueue(String queueName) {
        return jmsContext.createQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) {
        return jmsContext.createTopic(topicName);
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name) {
        return jmsContext.createDurableConsumer(topic, name);
    }

    @Override
    public JMSConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) {
        return jmsContext.createDurableConsumer(topic, name, messageSelector, noLocal);
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
        return jmsContext.createSharedConsumer(topic, name);
    }

    @Override
    public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
        return jmsContext.createSharedDurableConsumer(topic, name, messageSelector);
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
        return jmsContext.createSharedConsumer(topic, sharedSubscriptionName);
    }

    @Override
    public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) {
        return jmsContext.createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) {
        return jmsContext.createBrowser(queue);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) {
        return jmsContext.createBrowser(queue, messageSelector);
    }

    @Override
    public TemporaryQueue createTemporaryQueue() {
        return jmsContext.createTemporaryQueue();
    }

    @Override
    public TemporaryTopic createTemporaryTopic() {
        return jmsContext.createTemporaryTopic();
    }

    @Override
    public void unsubscribe(String name) {
        jmsContext.unsubscribe(name);
    }

    @Override
    public void acknowledge() {
        jmsContext.acknowledge();
    }
}
