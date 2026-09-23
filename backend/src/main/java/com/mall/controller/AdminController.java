package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Category;
import com.mall.entity.Order;
import com.mall.entity.Product;
import com.mall.entity.SystemConfig;
import com.mall.entity.User;
import com.mall.mapper.*;
import com.mall.service.UserService;
import com.mall.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.Date;

@RestController
@RequestMapping("/api/admin")
@Api(tags = "管理后台接口")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @GetMapping("/stats")
    @ApiOperation("统计数据")
    public R<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.selectList().size());
        stats.put("productCount", productMapper.countAll());
        stats.put("orderCount", orderMapper.countAll());
        stats.put("totalRevenue", orderMapper.sumRevenue());
        return R.ok(stats);
    }

    @GetMapping("/users")
    @ApiOperation("用户列表")
    public R<List<User>> users() {
        return R.ok(userService.getUserList());
    }

    @PostMapping("/user/status")
    @ApiOperation("更新用户状态")
    public R<Void> updateUserStatus(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        User target = userMapper.selectById(id);
        if (target != null && "ADMIN".equals(target.getRole())) {
            throw new com.mall.common.exception.BusinessException("管理员账号不可被禁用");
        }
        userService.updateUserStatus(id, status);
        return R.ok();
    }

    @GetMapping("/orders")
    @ApiOperation("所有订单")
    public R<Map<String, Object>> orders(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String orderNo) {
        return R.ok(orderService.getAllOrders(page, size, status, orderNo));
    }

    @GetMapping("/order/{id}")
    @ApiOperation("订单详情（管理端）")
    public R<Order> orderDetail(@PathVariable Long id) {
        // 管理员可查看任意订单，传 "ADMIN" 绕过用户归属校验
        return R.ok(orderService.getOrderDetail(id, null, "ADMIN"));
    }

    @PostMapping("/order/status")
    @ApiOperation("更新订单状态")
    public R<Void> updateOrderStatus(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        Object rawStatus = params.get("status");
        Integer status;
        if (rawStatus instanceof Number) {
            status = ((Number) rawStatus).intValue();
        } else {
            // 支持字符串 'pending'/'shipping'/'delivering'/'completed'/'cancelled'
            String s = rawStatus.toString();
            java.util.Map<String, Integer> m = new java.util.HashMap<>();
            m.put("pending", 0);
            m.put("shipping", 1);
            m.put("delivering", 2);
            m.put("completed", 3);
            m.put("cancelled", 4);
            if (!m.containsKey(s)) {
                throw new com.mall.common.exception.BusinessException("非法状态值: " + s);
            }
            status = m.get(s);
        }
        orderService.updateOrderStatus(id, status);
        return R.ok();
    }

    @GetMapping("/sales/report")
    @ApiOperation("销售趋势")
    public R<List<Map<String, Object>>> salesReport(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
        Map<String, Double> sumByDate = new HashMap<>();
        for (Order o : orderMapper.selectAll(0, 1000)) {
            String d = sdf.format(o.getCreateTime());
            sumByDate.merge(d, o.getTotalAmount().doubleValue(), Double::sum);
        }
        Calendar cal = Calendar.getInstance();
        for (int i = days - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            String d = sdf.format(cal.getTime());
            Map<String, Object> row = new HashMap<>();
            row.put("date", d);
            row.put("amount", sumByDate.getOrDefault(d, 0.0));
            result.add(row);
        }
        return R.ok(result);
    }

    @GetMapping("/product/stats")
    @ApiOperation("商品销量Top")
    public R<List<Map<String, Object>>> productStats() {
        List<Product> all = productMapper.selectPage(0, 1000, null);
        all.sort((a, b) -> Integer.compare(b.getSales() == null ? 0 : b.getSales(), a.getSales() == null ? 0 : a.getSales()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(5, all.size()); i++) {
            Product p = all.get(i);
            Map<String, Object> row = new HashMap<>();
            row.put("name", p.getName());
            row.put("sales", p.getSales() == null ? 0 : p.getSales());
            result.add(row);
        }
        return R.ok(result);
    }

    @GetMapping("/category/stats")
    @ApiOperation("分类占比")
    public R<List<Map<String, Object>>> categoryStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category c : categoryMapper.selectList()) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", c.getName());
            row.put("value", productMapper.countByCategoryId(c.getId()));
            result.add(row);
        }
        return R.ok(result);
    }

    @GetMapping("/config")
    @ApiOperation("获取系统配置")
    public R<Map<String, Object>> getConfig() {
        Map<String, Object> data = new HashMap<>();
        for (SystemConfig c : systemConfigMapper.selectAll()) {
            String key = c.getConfigKey();
            String value = c.getConfigValue();
            if (value == null) continue;
            if ("autoCancelMinutes".equals(key) || "autoConfirmDays".equals(key)) {
                try { data.put(key, Integer.valueOf(value)); } catch (Exception e) { data.put(key, 0); }
            } else {
                data.put(key, value);
            }
        }
        return R.ok(data);
    }

    @PostMapping("/config")
    @ApiOperation("保存系统配置")
    public R<Void> saveConfig(@RequestBody Map<String, Object> body) {
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            String value = val == null ? "" : String.valueOf(val);
            SystemConfig cfg = new SystemConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            systemConfigMapper.upsert(cfg);
        }
        return R.ok();
    }
}
