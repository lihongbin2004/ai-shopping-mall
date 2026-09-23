package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.User;
import com.mall.mapper.UserMapper;
import com.mall.security.JwtUtil;
import com.mall.service.UserService;
import com.mall.util.MD5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.mall.mapper.OrderMapper orderMapper;

    @Autowired
    private com.mall.mapper.FavoriteMapper favoriteMapper;

    @Autowired
    private com.mall.mapper.UserCouponMapper userCouponMapper;

    @Autowired
    private com.mall.mapper.AddressMapper addressMapper;

    @Autowired
    private com.mall.mapper.ReviewMapper reviewMapper;

    @Override
    public User register(User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
        User existing = userMapper.selectByUsername(user.getUsername());
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(MD5Util.encrypt(user.getPassword()));
        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(new Date());
        userMapper.insert(user);
        return user;
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            throw new BusinessException("用户名或密码不能为空");
        }
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        if (!user.getPassword().equals(MD5Util.encrypt(password))) {
            throw new BusinessException("密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", user);
        return result;
    }

    @Override
    public User getUserInfo(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    @Override
    public Map<String, Object> getUserStats(Long id) {
        Map<String, Object> stats = new HashMap<>();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 脱敏：去掉密码
        user.setPassword(null);
        // 订单统计
        int totalOrders = orderMapper.countByUserId(id);
        int pendingOrders = orderMapper.countByUserIdAndStatus(id, 0);
        int paidOrders = orderMapper.countByUserIdAndStatus(id, 1);
        int shippedOrders = orderMapper.countByUserIdAndStatus(id, 2);
        int finishedOrders = orderMapper.countByUserIdAndStatus(id, 3);
        // 累计消费（已支付/已发货/已完成）
        java.math.BigDecimal totalSpent = orderMapper.sumPaidAmountByUserId(id);
        // 收藏 / 评价
        int favCount = favoriteMapper.countByUserId(id);
        int reviewCount = reviewMapper.countByUserId(id);
        // 优惠券（可用 0=未使用）
        int couponCount = 0;
        try { couponCount = userCouponMapper.selectByUserId(id, 0).size(); } catch (Exception e) {}
        // 默认地址
        com.mall.entity.Address defaultAddress = null;
        try { defaultAddress = addressMapper.selectDefaultByUserId(id); } catch (Exception e) {}
        // 资料完整度：手机号/邮箱/头像/默认地址
        int complete = 0;
        if (user.getPhone() != null && !user.getPhone().isEmpty()) complete++;
        if (user.getEmail() != null && !user.getEmail().isEmpty()) complete++;
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) complete++;
        if (defaultAddress != null) complete++;
        // 封装
        stats.put("user", user);
        stats.put("totalOrders", totalOrders);
        stats.put("pendingOrders", pendingOrders);
        stats.put("paidOrders", paidOrders);
        stats.put("shippedOrders", shippedOrders);
        stats.put("finishedOrders", finishedOrders);
        stats.put("totalSpent", totalSpent);
        stats.put("favCount", favCount);
        stats.put("reviewCount", reviewCount);
        stats.put("couponCount", couponCount);
        stats.put("defaultAddress", defaultAddress);
        stats.put("profileComplete", complete);
        stats.put("profileTotal", 4);
        // 角色 / 状态翻译
        stats.put("roleLabel", "ADMIN".equals(user.getRole()) ? "管理员" : "普通用户");
        stats.put("statusLabel", user.getStatus() != null && user.getStatus() == 1 ? "正常" : "已禁用");
        return stats;
    }

    @Override
    public User updateUser(User user) {
        User existing = userMapper.selectById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getPhone() != null) {
            existing.setPhone(user.getPhone());
        }
        if (user.getEmail() != null) {
            existing.setEmail(user.getEmail());
        }
        if (user.getAvatar() != null) {
            existing.setAvatar(user.getAvatar());
        }
        userMapper.update(existing);
        existing.setPassword(null);
        return existing;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new BusinessException("请输入当前密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("新密码至少6位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 校验旧密码
        if (!user.getPassword().equals(MD5Util.encrypt(oldPassword))) {
            throw new BusinessException("当前密码错误");
        }
        // 加密新密码并更新
        user.setPassword(MD5Util.encrypt(newPassword));
        userMapper.update(user);
    }

    @Override
    public List<User> getUserList() {
        List<User> users = userMapper.selectList();
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    @Override
    public int updateUserStatus(Long id, Integer status) {
        return userMapper.updateStatus(id, status);
    }
}
