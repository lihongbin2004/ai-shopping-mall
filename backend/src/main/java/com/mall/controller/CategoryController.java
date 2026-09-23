package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Category;
import com.mall.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@Api(tags = "分类接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/tree")
    @ApiOperation("获取分类树")
    public R<List<Category>> tree() {
        return R.ok(categoryService.getTree());
    }

    @GetMapping("/list")
    @ApiOperation("获取分类列表")
    public R<List<Category>> list() {
        return R.ok(categoryService.getList());
    }

    @PostMapping("/create")
    @ApiOperation("创建分类")
    public R<Category> create(@RequestBody Category category) {
        return R.ok(categoryService.create(category));
    }

    @PutMapping("/update")
    @ApiOperation("更新分类")
    public R<Category> update(@RequestBody Category category) {
        return R.ok(categoryService.update(category));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除分类")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
