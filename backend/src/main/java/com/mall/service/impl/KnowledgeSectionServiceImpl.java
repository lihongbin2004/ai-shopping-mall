package com.mall.service.impl;

import com.mall.entity.KnowledgeSection;
import com.mall.mapper.KnowledgeSectionMapper;
import com.mall.service.IKnowledgeSectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeSectionServiceImpl implements IKnowledgeSectionService {

    @Autowired
    private KnowledgeSectionMapper mapper;

    @Override
    public List<KnowledgeSection> listEnabled() {
        return mapper.selectEnabled();
    }

    @Override
    public List<KnowledgeSection> listAll() {
        return mapper.selectAll();
    }

    @Override
    public KnowledgeSection getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public Long create(KnowledgeSection section) {
        if (section.getEnabled() == null) section.setEnabled(1);
        if (section.getSortOrder() == null) section.setSortOrder(0);
        if (section.getType() == null) section.setType("text");
        // 兜底：content 不能为空
        if (section.getContent() == null) section.setContent("");
        mapper.insert(section);
        return section.getId();
    }

    @Override
    public void update(KnowledgeSection section) {
        mapper.update(section);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public void toggleEnabled(Long id, Integer enabled) {
        mapper.updateEnabled(id, enabled);
    }
}
