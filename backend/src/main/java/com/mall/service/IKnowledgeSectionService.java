package com.mall.service;

import com.mall.entity.KnowledgeSection;

import java.util.List;

public interface IKnowledgeSectionService {
    /** AI 用：只取启用的，按 sort_order 升序 */
    List<KnowledgeSection> listEnabled();
    /** 管理员用：全部，含禁用 */
    List<KnowledgeSection> listAll();
    KnowledgeSection getById(Long id);
    Long create(KnowledgeSection section);
    void update(KnowledgeSection section);
    void delete(Long id);
    void toggleEnabled(Long id, Integer enabled);
}
