package com.mall.mapper;

import com.mall.entity.KnowledgeSection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeSectionMapper {

    /** 查所有启用的段落（按 sort_order 升序），AI prompt 用 */
    List<KnowledgeSection> selectEnabled();

    /** 管理员列表：返回全部（含禁用），按 sort_order 升序 */
    List<KnowledgeSection> selectAll();

    KnowledgeSection selectById(@Param("id") Long id);

    int insert(KnowledgeSection section);

    int update(KnowledgeSection section);

    int deleteById(@Param("id") Long id);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
