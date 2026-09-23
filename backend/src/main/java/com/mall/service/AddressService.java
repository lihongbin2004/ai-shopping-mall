package com.mall.service;

import com.mall.entity.Address;
import java.util.List;

public interface AddressService {
    List<Address> getAddressList(Long userId);
    Address createAddress(Address address);
    Address updateAddress(Address address);
    void deleteAddress(Long id);
    void setDefault(Long id, Long userId);
}
