package com.mall.service;

import com.mall.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> getTree();
    List<Category> getList();
    Category getById(Long id);
    Category create(Category category);
    Category update(Category category);
    void delete(Long id);
}
