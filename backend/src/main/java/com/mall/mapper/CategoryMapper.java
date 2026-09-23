package com.mall.mapper;

import com.mall.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CategoryMapper {
    Category selectById(Long id);
    List<Category> selectList();
    List<Category> selectByParentId(Long parentId);
    int insert(Category category);
    int update(Category category);
    int deleteById(Long id);
}
