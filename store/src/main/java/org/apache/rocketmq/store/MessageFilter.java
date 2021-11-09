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
package org.apache.rocketmq.store;

import java.nio.ByteBuffer;
import java.util.Map;


/**
 * RocketMQ: 消息过滤有两种模式
 * 类过滤classFilterMode，表达式模式Expression, 又分为ExpressionType.TAG和ExpressionType.SQL92
 *
 * TAG过滤，在服务端拉取时，会根据ConsumeQueue条目中存储的tag hashCode与订阅的tag(hashCode集合)进行匹配，匹配成功则放入待返回消息结果中
 * 然后在消息消费端(消费端，还会对消息的订阅消息字符串进行再一次过滤，为什么要进行两次过滤呢？ 主要就还是为了提高服务器端消费消息队列文件存储的性能)
 *
 * 如果直接进行字符串匹配，那么consumeQueue条目无法设置为定长结构，检索consumeQueue就不方便
 *
 *
 */
public interface MessageFilter {
    /**
     * match by tags code or filter bit map which is calculated when message received
     * and stored in consume queue ext.
     *
     * @param tagsCode tagsCode
     * @param cqExtUnit extend unit of consume queue
     */

    /**
     * 根据ConsumeQueue判断消息是否匹配
     * @param tagsCode 消息Tag的hashCode
     * @param cqExtUnit consumeQueue条目扩展属性
     * @return
     */
    boolean isMatchedByConsumeQueue(final Long tagsCode,
        final ConsumeQueueExt.CqExtUnit cqExtUnit);

    /**
     * match by message content which are stored in commit log.
     * <br>{@code msgBuffer} and {@code properties} are not all null.If invoked in store,
     * {@code properties} is null;If invoked in {@code PullRequestHoldService}, {@code msgBuffer} is null.
     *
     * @param msgBuffer message buffer in commit log, may be null if not invoked in store.
     * @param properties message properties, should decode from buffer if null by yourself.
     */
    /**
     * 根据存储在commitLog文件中的内容判断消息是否匹配
     * @param msgBuffer 消息内容，如果为空，该方法返回true
     * @param properties 消息属性，主要用于表达式SQL92过滤模式
     * @return
     */
    boolean isMatchedByCommitLog(final ByteBuffer msgBuffer,
        final Map<String, String> properties);
}
