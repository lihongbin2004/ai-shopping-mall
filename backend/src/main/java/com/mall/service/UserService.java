package com.mall.service;

import com.mall.entity.User;
import java.util.List;
import java.util.Map;

public interface UserService {
    User register(User user);
    Map<String, Object> login(String username, String password);
    User getUserInfo(Long id);
    Map<String, Object> getUserStats(Long id);
    User updateUser(User user);
    void changePassword(Long userId, String oldPassword, String newPassword);
    List<User> getUserList();
    int updateUserStatus(Long id, Integer status);
}
