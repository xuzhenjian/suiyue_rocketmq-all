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
package org.apache.rocketmq.store.index;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.MappedFile;

/**
 * 索引文件
 */
public class IndexFile {

    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);


    private static int hashSlotSize = 4;

    private static int indexSize = 20;

    private static int invalidIndex = 0;

    // 500W
    private final int hashSlotNum;
    // 2000W
    private final int indexNum;


    private final MappedFile mappedFile;
    private final FileChannel fileChannel;
    private final MappedByteBuffer mappedByteBuffer;
    private final IndexHeader indexHeader;

    public IndexFile(final String fileName, final int hashSlotNum, final int indexNum,
        final long endPhyOffset, final long endTimestamp) throws IOException {

        // INDEX_HEADER_SIZE 40字节 + hash槽 4字节 * 500w + index条目20字节 * 2000w
        int fileTotalSize = IndexHeader.INDEX_HEADER_SIZE + (hashSlotNum * hashSlotSize) + (indexNum * indexSize);

        this.mappedFile = new MappedFile(fileName, fileTotalSize);
        this.fileChannel = this.mappedFile.getFileChannel();
        this.mappedByteBuffer = this.mappedFile.getMappedByteBuffer();

        this.hashSlotNum = hashSlotNum;
        this.indexNum = indexNum;

        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        this.indexHeader = new IndexHeader(byteBuffer);

        if (endPhyOffset > 0) {
            this.indexHeader.setBeginPhyOffset(endPhyOffset);
            this.indexHeader.setEndPhyOffset(endPhyOffset);
        }

        if (endTimestamp > 0) {
            this.indexHeader.setBeginTimestamp(endTimestamp);
            this.indexHeader.setEndTimestamp(endTimestamp);
        }
    }

    public String getFileName() {
        return this.mappedFile.getFileName();
    }

    public void load() {
        this.indexHeader.load();
    }

    public void flush() {
        long beginTime = System.currentTimeMillis();
        if (this.mappedFile.hold()) {
            this.indexHeader.updateByteBuffer();
            this.mappedByteBuffer.force();
            this.mappedFile.release();
            log.info("flush index file elapsed time(ms) " + (System.currentTimeMillis() - beginTime));
        }
    }

    public boolean isWriteFull() {
        return this.indexHeader.getIndexCount() >= this.indexNum;
    }

    public boolean destroy(final long intervalForcibly) {
        return this.mappedFile.destroy(intervalForcibly);
    }

    /**
     * RocketMQ将消息索引键与消息偏移量映射关系写入到IndexFile
     * @param key  消息索引
     * @param phyOffset  消息物理偏移量
     * @param storeTimestamp  消息存储时间
     * @return
     */
    public boolean putKey(final String key, final long phyOffset, final long storeTimestamp) {

        /**
         * 如果当前已使用条目大于等于允许最大条目数时，则返回false，表示当前索引文件已写满
         *
         */
        if (this.indexHeader.getIndexCount() < this.indexNum) {

            /**
             * 如果当前索引文件未写满，则根据key算出hashcode
             *
             * 然后keyHash对hash槽数量取余，定位到hashCode对应的hash槽下标
             *
             * hashCode对应的hash槽的物理地址为 IndexHeader头部(40字节) + 下标*每个hash槽的大小
             *
             * 根据定位hash槽算法，如果不同key的hashcode值相同 或者 不同的key不同的hashcode但对hash槽数量取余后结果相同
             * 从而引发hash冲突，那IndexFile怎么解决这个问题呢？
             */
            int keyHash = indexKeyHashMethod(key);
            int slotPos = keyHash % this.hashSlotNum;
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;

            FileLock fileLock = null;

            try {

                // fileLock = this.fileChannel.lock(absSlotPos, hashSlotSize,
                // false);

                /**
                 * 读取hash槽中存储的数据，如果hash槽存储的数据小于0或者大于索引文件中的索引条目格式，则将slotValue设置为0
                 */
                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);

                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()) {
                    slotValue = invalidIndex;
                }

                // 计算待存储消息的时间戳与第一条消息时间戳的差值，并转换成秒
                long timeDiff = storeTimestamp - this.indexHeader.getBeginTimestamp();

                timeDiff = timeDiff / 1000;

                if (this.indexHeader.getBeginTimestamp() <= 0) {
                    timeDiff = 0;
                } else if (timeDiff > Integer.MAX_VALUE) {
                    timeDiff = Integer.MAX_VALUE;
                } else if (timeDiff < 0) {
                    timeDiff = 0;
                }

                /**
                 * 将条目信息存储在IndexFile
                 *  1.计算新添加条目的起始物理偏移量，等于头部字节长度+hash槽数量*单个hash槽大小(4个字节)+当前index条目个数*单个index条目大小(20个字节)
                 *  2.依次将hashcode,消息物理偏移量，消息时间戳与第一条消息时间戳的差值，当前hash槽的值存入MappedByteBuffer中
                 *  3. 将当前index中包含的条目数量存入Hash槽中，将覆盖原先Hash槽的值
                 */
                int absIndexPos = IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize + this.indexHeader.getIndexCount() * indexSize;

                this.mappedByteBuffer.putInt(absIndexPos, keyHash);

                this.mappedByteBuffer.putLong(absIndexPos + 4, phyOffset);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8, (int) timeDiff);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8 + 4, slotValue);

                this.mappedByteBuffer.putInt(absSlotPos, this.indexHeader.getIndexCount());


                /**
                 * 这里是Hash冲突链式解决方案的关键实现
                 * Hash槽中存储的是该HashCode所对应的最新的Index条目的下标，新的Index条目的最后4个字节存储该HashCode上一个条目的index下标
                 * 如果hash槽存储的值为0或大于当前IndexFile最大条目数或小于1，表示该Hash槽当前并没有对应的Index条目
                 *
                 * IndexFile条目中存储的不是消息索引KEY，而是消息属性KEY的HashCode，在根据Key查找需要根据消息物理偏移量找到消息，进而验证KEY的值
                 * 之所以只存储HashCode而不存储具体的KEY，是为了将Index设计为定长结构，才能方便地检索与定位条目
                 */

                /**
                 * 更新文件索引头信息。如果当前文件只包含一个条目，更新beginPhyOffset与beginTimestamp
                 */
                if (this.indexHeader.getIndexCount() <= 1) {
                    this.indexHeader.setBeginPhyOffset(phyOffset);
                    this.indexHeader.setBeginTimestamp(storeTimestamp);
                }

                if (invalidIndex == slotValue) {
                    this.indexHeader.incHashSlotCount();
                }
                this.indexHeader.incIndexCount();
                this.indexHeader.setEndPhyOffset(phyOffset);
                this.indexHeader.setEndTimestamp(storeTimestamp);

                return true;
            } catch (Exception e) {
                log.error("putKey exception, Key: " + key + " KeyHashCode: " + key.hashCode(), e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }
            }
        } else {
            log.warn("Over index file capacity: index count = " + this.indexHeader.getIndexCount()
                + "; index max num = " + this.indexNum);
        }

        return false;
    }

    public int indexKeyHashMethod(final String key) {
        int keyHash = key.hashCode();
        int keyHashPositive = Math.abs(keyHash);
        if (keyHashPositive < 0)
            keyHashPositive = 0;
        return keyHashPositive;
    }

    public long getBeginTimestamp() {
        return this.indexHeader.getBeginTimestamp();
    }

    public long getEndTimestamp() {
        return this.indexHeader.getEndTimestamp();
    }

    public long getEndPhyOffset() {
        return this.indexHeader.getEndPhyOffset();
    }

    public boolean isTimeMatched(final long begin, final long end) {
        boolean result = begin < this.indexHeader.getBeginTimestamp() && end > this.indexHeader.getEndTimestamp();
        result = result || (begin >= this.indexHeader.getBeginTimestamp() && begin <= this.indexHeader.getEndTimestamp());
        result = result || (end >= this.indexHeader.getBeginTimestamp() && end <= this.indexHeader.getEndTimestamp());
        return result;
    }

    /**
     * RocketMQ根据索引key查找消息的实现方法
     * @param phyOffsets 查找到的消息物理偏移量
     * @param key  索引KEY
     * @param maxNum 本次查找最大消息条数
     * @param begin 开始时间戳
     * @param end 结束时间戳
     * @param lock
     */
    public void selectPhyOffset(final List<Long> phyOffsets, final String key, final int maxNum,
        final long begin, final long end, boolean lock) {
        if (this.mappedFile.hold()) {

            /**
             * 根据key算出key的hashCode，然后keyHash对hash槽数量取余定位到hashCode对应的hash槽下标
             *
             * hashCode对应的hash槽的物理地址是 IndexHeader头部(40字节)+下标乘以每个hash槽的大小(4字节)
             */
            int keyHash = indexKeyHashMethod(key);
            int slotPos = keyHash % this.hashSlotNum;
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;

            FileLock fileLock = null;
            try {
                if (lock) {
                    // fileLock = this.fileChannel.lock(absSlotPos,
                    // hashSlotSize, true);
                }

                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);
                // if (fileLock != null) {
                // fileLock.release();
                // fileLock = null;
                // }

                /**
                 * 如果对应的hash槽中存储的数据小于1 或者 大于当前索引条目个数
                 * 则说明该HashCode没有对应的条目，直接返回
                 */
                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()
                    || this.indexHeader.getIndexCount() <= 1) {
                } else {

                    /**
                     * 由于会存在hash冲突，根据slotValue定位该hash槽最新的一个条目，将存储的物理偏移加入到phyOffsets中
                     * 然后继续验证Item条目中存储的上一个Index下标，如果大于等于1并且小于最大条目则继续查找，否则结束查找
                     */
                    for (int nextIndexToRead = slotValue; ; ) {
                        if (phyOffsets.size() >= maxNum) {
                            break;
                        }

                        /**
                         * 根据Index下标定位到条目的起始物理偏移量
                         * 然后依次读取hashCode、物理偏移量、时间差，上一个条目的index下标
                         */
                        int absIndexPos = IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize + nextIndexToRead * indexSize;

                        int keyHashRead = this.mappedByteBuffer.getInt(absIndexPos);
                        long phyOffsetRead = this.mappedByteBuffer.getLong(absIndexPos + 4);

                        long timeDiff = (long) this.mappedByteBuffer.getInt(absIndexPos + 4 + 8);
                        int prevIndexRead = this.mappedByteBuffer.getInt(absIndexPos + 4 + 8 + 4);


                        /**
                         * 如果存储的时间差小于0，则直接结束；如果hashCode匹配并且消息存储时间介于待查找时间start,end之间则将消息物理偏移量加入到PhyOffsets
                         * 并且验证条目的前一个Index索引，如果索引大于等于1并且小于Index条目数，则继续查找，否则结束整个查找
                         */
                        if (timeDiff < 0) {
                            break;
                        }

                        timeDiff *= 1000L;

                        long timeRead = this.indexHeader.getBeginTimestamp() + timeDiff;
                        boolean timeMatched = (timeRead >= begin) && (timeRead <= end);

                        if (keyHash == keyHashRead && timeMatched) {
                            phyOffsets.add(phyOffsetRead);
                        }

                        if (prevIndexRead <= invalidIndex
                            || prevIndexRead > this.indexHeader.getIndexCount()
                            || prevIndexRead == nextIndexToRead || timeRead < begin) {
                            break;
                        }

                        nextIndexToRead = prevIndexRead;
                    }
                }
            } catch (Exception e) {
                log.error("selectPhyOffset exception ", e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }

                this.mappedFile.release();
            }
        }
    }
}
