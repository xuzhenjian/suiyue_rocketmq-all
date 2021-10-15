/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.impl.producer;

import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.common.ThreadLocalIndex;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;

/**
 * 消息发送端采用重试机制，由retryTimesWhenSendFailed指定同步方式重试次数
 *
 * 异步重试机制在收到消息，发送数据结构后执行回调前重试，由retryTimesWhenSendAsyncFailed指定
 *
 * 选择消息队列，发送消息，发送成功则返回，收到异常则重试
 *
 * 选择消息队列有两种方式
 *  1. sendLatencyFaultEnable=false，默认不启用Broker故障延迟机制
 *  2. sendLatencyFaultEnable=true， 启用Broker故障延迟机制
 *
 *
 */
public class TopicPublishInfo {

    // 是否是顺序消息
    private boolean orderTopic = false;

    // 路由信息FLAG
    private boolean haveTopicRouterInfo = false;

    // 该主题的消息队列
    private List<MessageQueue> messageQueueList = new ArrayList<MessageQueue>();

    // 每选择一次消息队列，该值会自增1，如果Integer.MAX_VALUE，则重置为0，用于选择消息队列
    private volatile ThreadLocalIndex sendWhichQueue = new ThreadLocalIndex();

    // 主题的路由信息
    private TopicRouteData topicRouteData;

    public boolean isOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(boolean orderTopic) {
        this.orderTopic = orderTopic;
    }

    public boolean ok() {
        return null != this.messageQueueList && !this.messageQueueList.isEmpty();
    }

    public List<MessageQueue> getMessageQueueList() {
        return messageQueueList;
    }

    public void setMessageQueueList(List<MessageQueue> messageQueueList) {
        this.messageQueueList = messageQueueList;
    }

    public ThreadLocalIndex getSendWhichQueue() {
        return sendWhichQueue;
    }

    public void setSendWhichQueue(ThreadLocalIndex sendWhichQueue) {
        this.sendWhichQueue = sendWhichQueue;
    }

    public boolean isHaveTopicRouterInfo() {
        return haveTopicRouterInfo;
    }

    public void setHaveTopicRouterInfo(boolean haveTopicRouterInfo) {
        this.haveTopicRouterInfo = haveTopicRouterInfo;
    }

    /**
     * 在一次消息发送过程中，可能会多次执行选择消息队列这个方法，lastBrokerName就是上一次选择的执行发送消息失败的Broker
     * 第一次执行消息队列选择时，lastBrokerName为null，此时直接用sendWhichQueue自增再获取值
     * 与当前路由表中消息队列个数取模，返回该位置的MessageQueue
     *
     * 该算法在一次消息发送过程中能成功规避故障的Broker,但如果Broker宕机，由于路由算法中的消息队列是按Broker排序
     * 如果上一次根据路由算法选择的是宕机的Broker的第一个队列，那么随后的下次选择的是宕机Broker的第二个队列，消息发送很有可能会失败
     * 这样带来不必要的性能损耗，那么有什么方法在一次消息发送失败后，暂时将该Broker排除在消息队列选择范围外呢?
     *
     * Broker不可用后，路由信息为什么还会包含该Broker的路由信息呢？
     *
     * NameServer检测Broker是否可用是有延迟的，最短为一次心跳检测间隔10s
     * 其次，NameServer不会检测到Broker宕机后马上推送消息给消息生产者
     * 而是消费生产者每隔30s，更新一次路由信息，所以消息生产着最快感知Broker最新的路由也需要30s
     *
     * 如果能引入一种机制，在Broker宕机期间，如果一次消息发送失败后，就可以将Broker暂时排除在消息队列的选择范围中
     *
     * @param lastBrokerName
     * @return
     */
    public MessageQueue selectOneMessageQueue(final String lastBrokerName) {
        if (lastBrokerName == null) {
            return selectOneMessageQueue();
        } else {
            for (int i = 0; i < this.messageQueueList.size(); i++) {
                int index = this.sendWhichQueue.incrementAndGet();
                int pos = Math.abs(index) % this.messageQueueList.size();
                if (pos < 0)
                    pos = 0;
                MessageQueue mq = this.messageQueueList.get(pos);
                if (!mq.getBrokerName().equals(lastBrokerName)) {
                    return mq;
                }
            }
            return selectOneMessageQueue();
        }
    }

    public MessageQueue selectOneMessageQueue() {
        int index = this.sendWhichQueue.incrementAndGet();
        int pos = Math.abs(index) % this.messageQueueList.size();
        if (pos < 0)
            pos = 0;
        return this.messageQueueList.get(pos);
    }

    public int getQueueIdByBroker(final String brokerName) {
        for (int i = 0; i < topicRouteData.getQueueDatas().size(); i++) {
            final QueueData queueData = this.topicRouteData.getQueueDatas().get(i);
            if (queueData.getBrokerName().equals(brokerName)) {
                return queueData.getWriteQueueNums();
            }
        }

        return -1;
    }

    @Override
    public String toString() {
        return "TopicPublishInfo [orderTopic=" + orderTopic + ", messageQueueList=" + messageQueueList
            + ", sendWhichQueue=" + sendWhichQueue + ", haveTopicRouterInfo=" + haveTopicRouterInfo + "]";
    }

    public TopicRouteData getTopicRouteData() {
        return topicRouteData;
    }

    public void setTopicRouteData(final TopicRouteData topicRouteData) {
        this.topicRouteData = topicRouteData;
    }
}
