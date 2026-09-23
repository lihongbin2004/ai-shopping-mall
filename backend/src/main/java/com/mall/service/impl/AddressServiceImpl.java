package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.Address;
import com.mall.mapper.AddressMapper;
import com.mall.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public List<Address> getAddressList(Long userId) {
        return addressMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Address createAddress(Address address) {
        address.setCreateTime(new Date());
        if (address.getIsDefault() == 1) {
            addressMapper.resetDefault(address.getUserId());
        }
        addressMapper.insert(address);
        return address;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Address updateAddress(Address address) {
        Address existing = addressMapper.selectById(address.getId());
        if (existing == null) {
            throw new BusinessException("地址不存在");
        }
        if (address.getReceiver() != null) existing.setReceiver(address.getReceiver());
        if (address.getPhone() != null) existing.setPhone(address.getPhone());
        if (address.getProvince() != null) existing.setProvince(address.getProvince());
        if (address.getCity() != null) existing.setCity(address.getCity());
        if (address.getDistrict() != null) existing.setDistrict(address.getDistrict());
        if (address.getDetail() != null) existing.setDetail(address.getDetail());
        if (address.getIsDefault() != null) {
            if (address.getIsDefault() == 1) {
                addressMapper.resetDefault(address.getUserId());
            }
            existing.setIsDefault(address.getIsDefault());
        }
        addressMapper.update(existing);
        return existing;
    }

    @Override
    public void deleteAddress(Long id) {
        addressMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id, Long userId) {
        addressMapper.resetDefault(userId);
        addressMapper.setDefault(id, userId);
    }
}
