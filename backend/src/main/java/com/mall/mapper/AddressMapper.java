package com.mall.mapper;

import com.mall.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AddressMapper {
    Address selectById(Long id);
    List<Address> selectByUserId(Long userId);
    Address selectDefaultByUserId(Long userId);
    int insert(Address address);
    int update(Address address);
    int deleteById(Long id);
    int resetDefault(Long userId);
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
