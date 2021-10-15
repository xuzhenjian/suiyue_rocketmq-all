package org.apache.rocketmq.store;

/**
 * @author mayday_yueyue
 * @description TODO
 * @date 2021-10-14 15:46
 */
public class StoreMain {

    /**
     * CommitLog: 消息存储文件，所有消息主题的消息都存储在CommitLog文件中
     *
     * ConsumeQueue: 消息消费队列，消息到达CommitLog文件后，将异步转发到消息消费队列，供消息消费者消费
     * (Commitlog offset, size, tagHashCode)
     *
     * IndexFile: 消息索引文件，主要存储消息Key与Offset的对应关系
     * (Key hashCode: commitlog offset)
     *
     * 事务状态服务: 存储每条消息的事务状态
     *
     * 定时消息服务: 每一个延迟级别对应一个消息消费队列，存储延迟队列的消息拉取进度
     *
     */
}
