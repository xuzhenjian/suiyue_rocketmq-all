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

    /**
     * TransactionalMessageCheckService#run 该线程每1分钟频率进行事务回调操作，可通过transactionCheckInterval参数配置，单位ms
     *
     * TransactionalMessageService#check
     * 1.查询RMQ_SYS_TRANS_HALF_TOPIC主题下的消息队列，该主题是prepare消息的存储队列
     * 2.遍历每一个消息消费队列，每个消息消费队列的处理时间为60S
     * 3.根据RMQ_SYS_TRANS_HALF_TOPIC#queueId，获取对应RMQ_SYS_TRANS_HALF_TOPIC主题下的队列，该主题已经提交或者回滚的消息队列
     * 4.获取HALF, OP队列的当前进度
     * 5.根据OP队列当前的更新进度，往后获取32条消息
     * 6.判断需要发送回查的消息，其约束条件主要包括本地事务超时时间，消息有效性，以及消息是否已发送回查消息等
     * 7.异步发送回查消息
     * 8.更新HALF,OP队列的处理进度
     *
     * AbstractTransactionMessageCheckListener#resolveHalfMsg 展示异步发送消息回查进度
     * AbstractTransactionMessageCheckListener#sendCheckMessage 组装回查请求命令，根据生产者组，选择网络通道，从broker向生产者发送回查事务状态命令，CHECK,TRANSACTION_STATE
     * ClientRemotingProcessor#checkTransactionState
     * DefaultMQProducerImpl#checkTransactionState
     * TransactionListener#checkLocalTransaction 该方法中，由应用程序根据消息ID告诉RocketMQ该条消息的事务状态，是成功，还是回滚，还是未知
     * 根据事务状态发送END_TRANSACTION: 发送END_TRANSACTION命令给Broker，如果发送UNKNOWN,broker不会做任何动作，只会打印info级别日志
     */
}
