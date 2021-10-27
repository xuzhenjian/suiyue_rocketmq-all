package org.apache.rocketmq.store;

/**
 * @author mayday_yueyue
 * @description 关于Store模块的设计
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


    /**
     * RocketMQ存储路径为 ROCKET_HOME/store
     * commitlog: 消息存储目录
     * config: 运行期间的一些配置信息，主要包括以下信息
     *  consumerFilter.json: 主题消息过滤信息
     *  consumerOffset.json: 集群消费模式消息消费进度
     *  delayOffset.json: 延时消息队列拉取进度
     *  subscriptionGroup.json: 消息消费组配置信息
     *  topic.json: topic配置属性
     * consumequeue: 消息消费队列存储目录
     * index: 消息索引文件存储目录
     * abort: 如果存在abort文件说明Broker非正常关闭，该文件默认启动时创建，正常退出之前删除
     * checkpoint: 文件检测点，存储commitlog文件最后一次刷盘时间戳，consumequeue最后一次刷盘时间，index索引文件最后一次刷盘时间戳
     */

    /**
     * Index索引文件
     *
     * 消息消费队列是RocketMQ专门为消息订阅构建的索引文件，提高根据主题与消息队列检索消息的速度，另外RocketMQ引入了hash索引机制为消息建立索引
     * HashMap的设计包含两个基本点， hash槽与hash冲突的链表结构
     *
     * IndexFile总共包含IndexHeader、Hash槽，Hash条目(数据)
     *  1.IndexHeader头部，包括40个字节，记录该IndexFile的统计信息，其结构如下
     *  beginTimestamp: 该索引文件中包含消息的最小存储时间-------------------------------8字节
     *  endTimestamp: 该索引文件中包含消息的最大存储时间---------------------------------8字节
     *  beginPhyoffset: 该索引文件中包含消息的最小物理偏移量(commitlog文件偏移量)----------8字节
     *  endPhyoffset:   该索引文件中包含消息的最大物理偏移量-----------------------------8字节
     *  hashslotCount:  hashslot个数，并不是hash槽使用的个数，在这里意义不大--------------4字节
     *  indexCount: Index条目列表当前已经使用的个数，Index条目在index条目列表中按顺序存储----4字节
     *
     * 2.Hash槽，一个IndexFile默认包含500万个Hash槽，每个Hash槽存储的是落在该该hash槽的hashcode最新的index索引
     *
     * 3.Index条目列表，默认一个索引文件包含2000万个条目，每个Index条目结构如下：
     *  hashcode: key的hashcode----------------------------------------------4字节
     *  phyoffset: 消息对应的物理偏移量-----------------------------------------8字节
     *  timedif: 该消息存储时间与第一条消息的时间戳的差值，小于0该消息无效-------------4字节
     *  preIndexNo: 该条目的前一条记录的index索引，当出现hash冲突时构建的链表结构------4字节
     */

    /**
     * ConsumeQueue， 见ConsumeQueue.java
     */

    /**
     * CheckPoint文件
     * checkpoint的作用是记录CommitLog,ConsumeQueue,Index文件的刷盘时间点，文件固定长度为4K，其中只用该文件的前面24个字节
     *
     * -------8字节-------|------8字节------|--------8字节--------|
     * physicMsgTimestamp|logicMsgTimestamp|indexMsgTimestamp  |
     *
     * physicMsgTimestamp: commitlog文件刷盘时间点
     * logicMsgTimestamp: 消息消费队列文件刷盘时间点
     * indexMsgTimestamp: 索引文件刷盘时间点
     **/

    /**
     * RocketMQ的存储与读写是基于JDK NIO的内存映射机制(MappedByteBuffer)的，消息存储时首先将消息追加到内存，再根据配置的刷盘策略在不同时间进行刷写磁盘
     * 如果是同步刷盘，消息追加到内存后，将同步调用MappedByteBuffer的force()方法
     * 如果是异步刷盘，在消息追加到内存后立马返回给消息发送端。RocketMQ使用单独的线程按照某一个设定的频率执行刷盘操作。
     * 通过在Broker配置文件中配置flushDiskType来设定刷盘方式，可选值ASYNC_FLUSH, SYNC_FLUSH，默认为异步刷盘
     *
     * RocketMQ处理刷盘的实现方式为Commitlog#submitFlushRequest
     *
     * ConsumeQueue,IndexFile刷盘的实现原理与CommitLog刷盘机制类似
     * 注意： 索引文件的刷盘并不是定时刷盘机制，而是每更新一次索引文件就会将上一次的改动刷写到磁盘
     *
     */

    /**
     * 过期文件删除机制
     * 由于RocketMQ操作CommmitLog, ConsumeQueue文件是基于内存映射机制并且在启动的时候会加载commitLog,ConsumeQueue目录下的所有文件
     * 为了避免内存与磁盘的浪费，不可能将消息永久存储在消息服务器上，所以需要引入一种机制来删除已过期的文件
     *
     * RocketMQ顺序写CommitLog，ConsumeQueue文件，所有写操作全部落在最后一个CommitLog或ConsumeQueue文件上，之前的文件在下一个文件创建后将不会再被更新
     * RocketMQ清除过期文件的方法是， 如果非当前写文件在一定时间间隔内没有再次被更新，则认为是过期文件，可以被删除，RocketMQ不会关注这个文件上的消息是否被消费
     *
     * 默认每个文件的过期时间为72个小时，通过Broker配置文件中设置fileReservedTime来改变过期时间，单位为小时
     *
     *
     */

    /**
     * 小结：
     * RocketMQ主要存储文件包含commitlog, consumequeue,indexFile，checkpoint, abort
     * 单个消息存储文件，消息消费队列文件，Hash索引文件长度固定以便使用内存映射机制进行文件的读写操作
     *
     * RocketMQ组织文件以文件的起始偏移量来命名文件，这样根据偏移量能快速定位到真实的物理文件。
     * RocketMQ基于内存映射文件机制提供了同步刷盘与异步刷盘两种机制
     * 异步刷盘是指在消息存储时先追加到内存映射文件，然后启动专门的刷盘线程定时将内存中的数据刷写到磁盘
     *
     *
     * CommitLog，消息存储文件，RocketMQ为了保证消息发送的高吞吐量，采用单一文件存储所有主题的消息，保证消息存储是完全的顺序写
     * 但这样给文件读取同样带了不便，但这样给文件读取同样带来了不便
     * 为此RocketMQ为了方便消息消费构建了消息消费队列文件，基于主题与队列进行组织，同时RocketMQ为消息实现了hash索引
     * 可以为消息设置索引键，根据索引能够快速从CommitLog文件中检索消息
     *
     * 当消息到达CommitLog文件后，会通过ReputMessageService线程接近实时地将消息转发给消息消费队列文件与索引文件
     * 为了安全起见，RocketMQ引入abort文件，记录Broker的停机是正常关闭还是异常关闭
     * 在重启Broker时为了保证CommitLog文件，消息消费队列文件与hash索引文件的正确性，分别采取不同的策略来恢复文件
     *
     * RocketMQ不会永久存储消息文件，消息消费队列文件，而是启用文件过期机制并在磁盘空间不足或默认在凌晨4点删除过期文件
     * 文件默认保存72小时并且在删除文件时并不会判断该消息文件上的消息是否被消费
     *
     */
}
