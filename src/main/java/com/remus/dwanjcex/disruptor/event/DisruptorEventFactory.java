package com.remus.dwanjcex.disruptor.event;

import com.lmax.disruptor.EventFactory;

/**
 * Disruptor事件工厂。
 * 负责在RingBuffer初始化时预创建事件对象，以避免在运行时动态创建对象，从而减少垃圾回收。
 */
public class DisruptorEventFactory implements EventFactory<DisruptorEvent> {

    /**
     * 当Disruptor启动并填充RingBuffer时，会调用此方法来创建事件的实例。
     * @return 一个新的、空的DisruptorEvent对象。
     */
    @Override
    public DisruptorEvent newInstance() {
        return new DisruptorEvent();
    }
}
