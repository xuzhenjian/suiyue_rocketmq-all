* 1.RocketMQ在线扩容队列，可以通过UpdateTopicSubCommand
    集群扩容的时候，需要同步在集群上的topic.json，subScriptionGroup.json文件
    消费者向Broker发起消息拉取请求时，如果broker上并没有存在该消费组的订阅消息时，
    如果不允许自动创建(autoCreateSubscriptionGroup设置false，默认为true)，则不会返回消息给客户端