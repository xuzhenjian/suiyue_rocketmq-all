package org.apache.rocketmq.client;

/**
 * @author mayday_yueyue
 * @description TODO
 * @date 2021-11-12 17:19
 */
public class TransactionMain {

    /**
     * 4.3.0版本，此版本解决了RocketMQ对事务的支持
     *
     * 1.应用程序在事务内完成相关业务数据落库后，需要同步调用RocketMQ消息发送接口，发送状态为prepare的消息
     * 消息发送成功后，RocketMQ服务器会回调RocketMQ消息发送者的事件监听程序，记录消息的本地事务状态
     * 该相关标记与本地业务操作同属一个事务，确保消息发送与本地事务的原子性
     *
     * 2.RocketMQ在收到类型为prepare的消息时，会首先备份消息的原主题与原消息消费队列，然后将消息存储在
     * 主题为RMQ_SYS_TRANS_HALF_TOPIC的消息消费队列中
     *
     * 3.RocketMQ消息服务器开启一个定时任务，消费RMQ_SYS_TRANS_HALF_TOPIC的消息，向消息发送端发起消息事务状态回查
     * 应用程序根据保存的事务状态回馈给消息服务器事务的状态，来进行提交或者回滚的操作
     *
     * 如果未知，那么待下一次的回查，RocketMQ允许设置一条消息的回查间隔与回查次数
     * 如果在超时回查次数后依然无法获知消息的事务状态，则默认回滚消息
     *
     */
}
