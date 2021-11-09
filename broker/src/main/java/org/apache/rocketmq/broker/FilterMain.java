package org.apache.rocketmq.broker;

/**
 * @author mayday_yueyue
 * @description TODO
 * @date 2021-11-08 18:51
 */
public class FilterMain {

    /**
     * RocketMQ允许消息消费者自定义消息过滤实现类并将其代码上传到FilterServer上
     *
     * 消息消费者向FilterServer拉取消息，FilterServer将消息消费者的拉取命令转发到Broker
     * 然后对返回的消息执行消息过滤逻辑，最终将消息返回给消费端
     *
     *
     * 1.Broker进程所在的服务器会启动多个FilterServer进程
     * 2.消费者在订阅消息主题时会上传一个自定义的消息过滤实现类，FilterServer加载并实例化
     * 3.消息消费者向FilterServer发送消息拉取请求，FilterServer收到消息消费者消息拉取请求后，FilterServer将消息拉取请求转发给Broker
     * Broker返回消息后在Filter端执行消息过滤逻辑，然后返回符合订阅信息的消息给消息消费者进行消费
     *
     *
     */
}
