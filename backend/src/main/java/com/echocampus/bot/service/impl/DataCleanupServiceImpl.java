package com.echocampus.bot.service.impl;

import com.echocampus.bot.mapper.ConversationMapper;
import com.echocampus.bot.mapper.MessageMapper;
import com.echocampus.bot.service.DataCleanupService;
import com.echocampus.bot.utils.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据清理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupServiceImpl implements DataCleanupService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    @Value("${data-cleanup.retention-minutes:1}")
    private int retentionMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredDeletedData() {
        log.info("开始清理过期的软删除数据，保留分钟数: {}", retentionMinutes);

        // 计算截止日期：当前时间减去保留分钟数
        LocalDateTime beforeDate = DateTimeUtil.now().minusMinutes(retentionMinutes);
        log.info("清理截止日期: {}", beforeDate);

        // 1. 查询需要清理的对话ID列表
        List<Long> conversationIds = conversationMapper.selectDeletedConversationIdsBefore(beforeDate);
        
        if (conversationIds == null || conversationIds.isEmpty()) {
            log.info("没有需要清理的数据");
            return 0;
        }

        log.info("找到 {} 条需要清理的对话记录", conversationIds.size());

        int totalCleaned = 0;

        try {
            // 2. 先删除关联的消息（避免外键约束问题）
            int messagesDeleted = messageMapper.physicalDeleteByConversationIds(conversationIds);
            log.info("已删除 {} 条消息记录", messagesDeleted);

            // 3. 删除对话记录
            int conversationsDeleted = conversationMapper.physicalDeleteByIds(conversationIds);
            log.info("已删除 {} 条对话记录", conversationsDeleted);

            totalCleaned = conversationsDeleted;

            log.info("数据清理完成，共清理 {} 条对话和 {} 条消息", conversationsDeleted, messagesDeleted);
        } catch (Exception e) {
            log.error("清理数据时发生错误", e);
            throw e;
        }

        return totalCleaned;
    }
}
