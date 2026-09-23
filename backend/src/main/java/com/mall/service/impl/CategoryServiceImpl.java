package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Category;
import com.mall.mapper.CategoryMapper;
import com.mall.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public List<Category> getTree() {
        List<Category> all = categoryMapper.selectList();
        List<Category> roots = all.stream().filter(c -> c.getParentId() == null || c.getParentId() == 0).collect(Collectors.toList());
        for (Category root : roots) {
            buildChildren(root, all);
        }
        return roots;
    }

    private void buildChildren(Category parent, List<Category> all) {
        List<Category> children = all.stream()
                .filter(c -> parent.getId().equals(c.getParentId()))
                .collect(Collectors.toList());
        parent.setChildren(children);
        for (Category child : children) {
            buildChildren(child, all);
        }
    }

    @Override
    public List<Category> getList() {
        return categoryMapper.selectList();
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public Category create(Category category) {
        category.setCreateTime(new Date());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public Category update(Category category) {
        Category existing = categoryMapper.selectById(category.getId());
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        if (category.getName() != null) {
            existing.setName(category.getName());
        }
        if (category.getSort() != null) {
            existing.setSort(category.getSort());
        }
        categoryMapper.update(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        List<Category> children = categoryMapper.selectByParentId(id);
        if (children != null && !children.isEmpty()) {
            throw new BusinessException("该分类下有子分类，无法删除");
        }
        categoryMapper.deleteById(id);
    }
}
