package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.User;
import com.mall.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Api(tags = "用户接口")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    @ApiOperation("用户注册")
    public R<User> register(@RequestBody User user) {
        return R.ok(userService.register(user));
    }

    @PostMapping("/login")
    @ApiOperation("用户登录")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        return R.ok(userService.login(params.get("username"), params.get("password")));
    }

    @GetMapping("/info")
    @ApiOperation("获取用户信息")
    public R<User> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(userService.getUserInfo(userId));
    }

    @GetMapping("/stats")
    @ApiOperation("用户主页统计信息")
    public R<Map<String, Object>> stats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return R.ok(userService.getUserStats(userId));
    }

    @PutMapping("/update")
    @ApiOperation("更新用户信息")
    public R<User> update(@RequestBody User user, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        user.setId(userId);
        // 安全：update 接口永远不允许通过它直接修改密码，必须用 changePassword
        user.setPassword(null);
        return R.ok(userService.updateUser(user));
    }

    @PutMapping("/change-password")
    @ApiOperation("修改密码（需要校验当前密码）")
    public R<Void> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new com.mall.common.exception.BusinessException("请输入当前密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new com.mall.common.exception.BusinessException("新密码至少6位");
        }
        userService.changePassword(userId, oldPassword, newPassword);
        return R.ok();
    }
}
