package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.KnowledgeSection;
import com.mall.service.IKnowledgeSectionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台 — 知识库管理（CRUD）
 * 前台 / 前端 admin 用：/admin/knowledge/**
 */
@RestController
@RequestMapping("/api/admin/knowledge")
@Api(tags = "后台-知识库管理")
public class AdminKnowledgeController {

    @Autowired
    private IKnowledgeSectionService service;

    @GetMapping("/list")
    @ApiOperation("知识库段落列表（含禁用）")
    public R<List<KnowledgeSection>> list() {
        return R.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @ApiOperation("知识库段落详情")
    public R<KnowledgeSection> detail(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @PostMapping("/create")
    @ApiOperation("新增知识库段落")
    public R<Long> create(@RequestBody KnowledgeSection section) {
        if (section.getName() == null || section.getName().trim().isEmpty()) {
            return R.error("段落名不能为空");
        }
        if (section.getContent() == null) {
            return R.error("内容不能为空");
        }
        Long id = service.create(section);
        return R.ok(id);
    }

    @PostMapping("/update")
    @ApiOperation("更新知识库段落")
    public R<Void> update(@RequestBody KnowledgeSection section) {
        if (section.getId() == null) return R.error("id 不能为空");
        service.update(section);
        return R.ok();
    }

    @PostMapping("/delete")
    @ApiOperation("删除知识库段落")
    public R<Void> delete(@RequestBody KnowledgeSection section) {
        if (section.getId() == null) return R.error("id 不能为空");
        service.delete(section.getId());
        return R.ok();
    }

    @PostMapping("/toggle")
    @ApiOperation("启用/禁用知识库段落")
    public R<Void> toggle(@RequestBody KnowledgeSection section) {
        if (section.getId() == null || section.getEnabled() == null) {
            return R.error("参数不完整");
        }
        service.toggleEnabled(section.getId(), section.getEnabled());
        return R.ok();
    }
}
