package com.mall.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * RAG 知识库段落（后台可编辑）
 * type 字段：
 *  - text : 纯文本（content 直接拼到 system prompt）
 *  - list : JSON 数组字符串（多条短句，渲染为列表）
 *  - kv   : 自由 JSON 对象（key-value 对，给 AI 当参考资料）
 */
@Data
public class KnowledgeSection {
    private Long id;
    private String name;
    private String type;
    private String content;
    private Integer sortOrder;
    private Integer enabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
