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
package org.apache.rocketmq.client.impl.consumer;

import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;

/**
 * 消息队列负载与重新分布机制
 *
 * PullMessageService在启动时由于LinkedBlockingQueue<PullRequest> pullRequestQueue中没有PullRequest对象，故PullMessageService线程将阻塞
 *
 * 1.PullRequest对象在什么时候创建并加入到pullRequestQueue中，以便唤醒PullMessageService线程
 * 2.集群内多个消费者是如何负载主题下的多个消费队列，并且如果有新的消费者加入时，消息队列又会如何重新分布
 *
 *
 * RocketMQ消息队列重新分布是由RebalanceService线程来实现的，一个MQClientInstance持有一个RebalanceService实现，并随着MQClientInstance的启动而启动
 *
 *
 */

/**
 * 1.RebalanceService线程每隔20s对消费者订阅的主题进行一次队列重新分配，每一次分配都会获取主题的所有队列，从Broker服务器实时查询当前该主题该消费组内消费者列表
 * 对新分配的消息队列会创建对应的PullRequest对象，在一个JVM进程中，同一个消费组同一个队列只会存在一个PullRequest对象
 *
 * 2.由于每次进行队列负载时都会从Broker实时查询当前消费组所有消费者，并且对消费队列，消费者列表进行排序，这样新加入的消费者就会在队列重新分布时，分配到消费者队列从而消费消息
 */
public class RebalanceService extends ServiceThread {

    private static long waitInterval =
        Long.parseLong(System.getProperty(
            "rocketmq.client.rebalance.waitInterval", "20000"));


    private final InternalLogger log = ClientLogger.getLog();
    private final MQClientInstance mqClientFactory;

    public RebalanceService(MQClientInstance mqClientFactory) {
        this.mqClientFactory = mqClientFactory;
    }

    /**
     * RebalanceService线程默认每隔20S, 执行一次mqClientFactory.doRebalance()方法
     * 可以使用-Drocketmq.client.rebalance.waitInterval=interval来改变默认值
     */
    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            this.waitForRunning(waitInterval);
            this.mqClientFactory.doRebalance();
        }

        log.info(this.getServiceName() + " service end");
    }

    @Override
    public String getServiceName() {
        return RebalanceService.class.getSimpleName();
    }
}
