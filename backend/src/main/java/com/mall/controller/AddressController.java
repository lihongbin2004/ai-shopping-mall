package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Address;
import com.mall.service.AddressService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/address")
@Api(tags = "地址接口")
public class AddressController {

    @Autowired
    private AddressService addressService;

    @GetMapping("/list")
    @ApiOperation("获取地址列表")
    public R<List<Address>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(addressService.getAddressList(userId));
    }

    @PostMapping("/create")
    @ApiOperation("创建地址")
    public R<Address> create(@RequestBody Address address, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        address.setUserId(userId);
        return R.ok(addressService.createAddress(address));
    }

    @PutMapping("/update")
    @ApiOperation("更新地址")
    public R<Address> update(@RequestBody Address address, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        address.setUserId(userId);
        return R.ok(addressService.updateAddress(address));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除地址")
    public R<Void> delete(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return R.ok();
    }

    @PostMapping("/setDefault")
    @ApiOperation("设置默认地址")
    public R<Void> setDefault(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long id = Long.valueOf(params.get("id").toString());
        addressService.setDefault(id, userId);
        return R.ok();
    }
}
