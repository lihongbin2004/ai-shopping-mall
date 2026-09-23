package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Address;
import com.mall.entity.Order;
import com.mall.mapper.AddressMapper;
import com.mall.mapper.OrderMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 物流轨迹模拟接口
 * 根据订单状态生成模拟的物流轨迹数据（含城市坐标、时间节点）
 */
@RestController
@RequestMapping("/api/logistics")
@Api(tags = "物流轨迹接口")
public class LogisticsController {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AddressMapper addressMapper;

    /** 模拟的城市仓库坐标（经度, 纬度） */
    private static final String[][] CITIES = {
            {"上海", "121.4737", "31.2304"},
            {"杭州", "120.1551", "30.2741"},
            {"南京", "118.7969", "32.0603"},
            {"北京", "116.4074", "39.9042"},
            {"广州", "113.2644", "23.1291"},
            {"深圳", "114.0579", "22.5431"},
            {"成都", "104.0668", "30.5728"},
            {"武汉", "114.3055", "30.5928"},
            {"西安", "108.9402", "34.3416"},
            {"郑州", "113.6254", "34.7466"},
            {"重庆", "106.5516", "29.5630"},
            {"长沙", "112.9388", "28.2282"},
            {"邵阳", "111.4692", "27.2455"},
            {"合肥", "117.2830", "31.8612"},
            {"福州", "119.2965", "26.0745"},
            {"青岛", "120.3826", "36.0671"},
            {"沈阳", "123.4315", "41.8057"},
            {"天津", "117.2010", "39.1256"},
            {"济南", "117.0009", "36.6512"},
            {"昆明", "102.8329", "24.8801"},
            {"厦门", "118.0894", "24.4798"}
    };

    /** 快递公司池 */
    private static final String[] COMPANIES = {"顺丰速运", "京东物流", "中通快递", "圆通速递", "韵达快递", "申通快递"};

    @GetMapping("/track/{orderId}")
    @ApiOperation("查询模拟物流轨迹")
    public R<Map<String, Object>> track(@PathVariable Long orderId, HttpServletRequest request) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.error("订单不存在");
        }
        // 权限校验
        String role = (String) request.getAttribute("role");
        Long userId = (Long) request.getAttribute("userId");
        if (!"ADMIN".equals(role) && !order.getUserId().equals(userId)) {
            return R.error("无权查看该订单");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("company", pickCompany(orderId));
        result.put("trackingNo", "SF" + orderId + System.currentTimeMillis() % 100000);
        result.put("tracks", generateTracks(order));
        return R.ok(result);
    }

    /** 根据订单状态和 ID 生成模拟轨迹 */
    private List<Map<String, Object>> generateTracks(Order order) {
        int status = order.getStatus() == null ? 0 : order.getStatus();
        Date baseTime = order.getCreateTime();
        if (baseTime == null) baseTime = new Date();

        // 起点：商家仓库（固定杭州）
        int fromIdx = findCityIndex("杭州");
        if (fromIdx < 0) fromIdx = 1;

        // 终点：根据订单收货地址匹配城市
        int toIdx = findDestinationCity(order);
        if (toIdx < 0 || toIdx == fromIdx) {
            // 匹配不到就用随机城市兜底
            Random rnd = new Random(order.getId() == null ? 1 : order.getId());
            toIdx = rnd.nextInt(CITIES.length);
            if (toIdx == fromIdx) toIdx = (toIdx + 1) % CITIES.length;
        }

        // 生成 2~4 个途经点：按地理就近排序（起点→中转站→终点）
        Random rnd = new Random(order.getId() == null ? 1 : order.getId());
        int waypointCount = 2 + rnd.nextInt(3);

        // 筛选候选城市（排除起终点）
        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < CITIES.length; i++) {
            if (i == fromIdx || i == toIdx) continue;
            // 计算"起点→候选点→终点"的总距离（欧氏距离近似）
            double dist = geoDistance(CITIES[fromIdx], CITIES[i]) + geoDistance(CITIES[i], CITIES[toIdx]);
            candidates.add(new int[]{i, (int)(dist * 1000)});
        }
        // 按总距离升序排（沿途的先选）
        candidates.sort((a, b) -> Integer.compare(a[1], b[1]));

        // 从排好序的候选中取前 N 个（加一点随机扰动，避免所有订单都走一样的点）
        List<Integer> midPoints = new ArrayList<>();
        int take = Math.min(waypointCount, candidates.size());
        for (int i = 0; i < take; i++) {
            // 前 60% 确定取，后 40% 随机跳过 → 保证主线稳定但有变化
            if (rnd.nextDouble() < 0.6 || midPoints.size() < take - 1) {
                midPoints.add(candidates.get(i)[0]);
            }
        }
        // 不够就继续补
        int ci = take;
        while (midPoints.size() < take && ci < candidates.size()) {
            midPoints.add(candidates.get(ci++)[0]);
        }
        // 对中途点按"距起点距离"再排一次，保证路线顺序合理
        final int finalFromIdx = fromIdx;
        midPoints.sort((a, b) -> {
            double da = geoDistance(CITIES[finalFromIdx], CITIES[a]);
            double db = geoDistance(CITIES[finalFromIdx], CITIES[b]);
            return Double.compare(da, db);
        });

        List<Integer> waypoints = new ArrayList<>();
        waypoints.add(fromIdx);
        waypoints.addAll(midPoints);
        waypoints.add(toIdx);

        // 根据订单状态决定轨迹展示到哪一步
        // status 0=pending(待付款) → 无轨迹
        // status 1=shipping(待发货) → 只有"已下单"
        // status 2=delivering(待收货) → 运输中，展示到倒数第2个点
        // status 3=completed(已完成) → 全部轨迹
        // status 4=cancelled → 只有"已下单"+"已取消"
        // status 5=refunded → 只有"已下单"+"已退款"

        List<Map<String, Object>> tracks = new ArrayList<>();

        switch (status) {
            case 0: // 待付款
                addTrack(tracks, baseTime, "INFO", "订单已创建，等待付款", CITIES[fromIdx]);
                break;
            case 1: // 待发货
                addTrack(tracks, baseTime, "INFO", "订单已付款，商家准备发货", CITIES[fromIdx]);
                break;
            case 4: // 已取消
                addTrack(tracks, baseTime, "INFO", "订单已创建", CITIES[fromIdx]);
                addTrack(tracks, offset(baseTime, 1), "ERROR", "订单已取消", CITIES[fromIdx]);
                break;
            case 5: // 已退款
                addTrack(tracks, baseTime, "INFO", "订单已创建", CITIES[fromIdx]);
                addTrack(tracks, offset(baseTime, 1), "INFO", "订单已付款", CITIES[fromIdx]);
                addTrack(tracks, offset(baseTime, 2), "WARNING", "订单已退款", CITIES[fromIdx]);
                break;
            case 2: // 运输中 —— 展示到倒数第2个点
                int showUpTo = Math.max(1, waypoints.size() - 1);
                for (int i = 0; i < showUpTo; i++) {
                    String desc;
                    String type;
                    if (i == 0) {
                        desc = "快递已揽收";
                        type = "SUCCESS";
                    } else if (i == showUpTo - 1) {
                        desc = "运输途中，下一站 " + CITIES[waypoints.get(i + 1)][0];
                        type = "WARNING";
                    } else {
                        desc = "已到达 " + CITIES[waypoints.get(i)][0] + " 转运中心";
                        type = "INFO";
                    }
                    addTrack(tracks, offset(baseTime, i * 3L + 1), type, desc, CITIES[waypoints.get(i)]);
                }
                break;
            case 3: // 已完成 —— 全部轨迹
            default:
                for (int i = 0; i < waypoints.size(); i++) {
                    String desc;
                    String type;
                    if (i == 0) {
                        desc = "快递已揽收";
                        type = "SUCCESS";
                    } else if (i == waypoints.size() - 1) {
                        desc = "已签收，感谢您的订购！";
                        type = "SUCCESS";
                    } else {
                        desc = "已到达 " + CITIES[waypoints.get(i)][0] + " 转运中心";
                        type = "INFO";
                    }
                    addTrack(tracks, offset(baseTime, i * 3L + 1), type, desc, CITIES[waypoints.get(i)]);
                }
                break;
        }

        return tracks;
    }

    private void addTrack(List<Map<String, Object>> tracks, Date time, String type, String desc, String[] city) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("time", time);
        t.put("type", type);        // SUCCESS / INFO / WARNING / ERROR
        t.put("description", desc);
        t.put("city", city[0]);
        t.put("lng", Double.parseDouble(city[1]));
        t.put("lat", Double.parseDouble(city[2]));
        tracks.add(t);
    }

    private Date offset(Date base, long hours) {
        return new Date(base.getTime() + hours * 3600 * 1000L);
    }

    private String pickCompany(Long orderId) {
        Random rnd = new Random(orderId == null ? 1 : orderId);
        return COMPANIES[rnd.nextInt(COMPANIES.length)];
    }

    /** 在 CITIES 数组中查找城市下标 */
    private int findCityIndex(String cityName) {
        if (cityName == null) return -1;
        for (int i = 0; i < CITIES.length; i++) {
            if (CITIES[i][0].equals(cityName)) return i;
        }
        return -1;
    }

    /** 两点经纬度的欧氏距离（用于粗略排序，不需要精确公里数） */
    private double geoDistance(String[] a, String[] b) {
        double dx = Double.parseDouble(a[1]) - Double.parseDouble(b[1]);
        double dy = Double.parseDouble(a[2]) - Double.parseDouble(b[2]);
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** 根据订单收货地址匹配终点城市 */
    private int findDestinationCity(Order order) {
        // 优先解析 addressSnapshot
        String addressDetail = null;
        if (order.getAddressSnapshot() != null && !order.getAddressSnapshot().isEmpty()) {
            try {
                com.alibaba.fastjson2.JSONObject snap = com.alibaba.fastjson2.JSON.parseObject(order.getAddressSnapshot());
                StringBuilder sb = new StringBuilder();
                if (snap.getString("province") != null) sb.append(snap.getString("province"));
                if (snap.getString("city") != null) sb.append(snap.getString("city"));
                if (snap.getString("district") != null) sb.append(snap.getString("district"));
                if (snap.getString("detail") != null) sb.append(snap.getString("detail"));
                addressDetail = sb.toString();
            } catch (Exception ignored) {}
        }
        // 回退：查 address 表
        if ((addressDetail == null || addressDetail.isEmpty()) && order.getAddressId() != null) {
            com.mall.entity.Address addr = addressMapper.selectById(order.getAddressId());
            if (addr != null) {
                StringBuilder sb = new StringBuilder();
                if (addr.getProvince() != null) sb.append(addr.getProvince());
                if (addr.getCity() != null) sb.append(addr.getCity());
                if (addr.getDistrict() != null) sb.append(addr.getDistrict());
                if (addr.getDetail() != null) sb.append(addr.getDetail());
                addressDetail = sb.toString();
            }
        }

        if (addressDetail == null || addressDetail.isEmpty()) return -1;

        // 从 CITIES 中找：优先精确匹配 city 字段，其次在完整地址中找城市名
        // 先尝试匹配 CITIES 中的城市名
        for (int i = 0; i < CITIES.length; i++) {
            if (addressDetail.contains(CITIES[i][0])) return i;
        }

        // 处理带"市"后缀的情况（比如"邵阳市"→匹配"邵阳"）
        for (int i = 0; i < CITIES.length; i++) {
            String cityShort = CITIES[i][0];
            if (addressDetail.contains(cityShort + "市") || addressDetail.contains(cityShort + "地区")) {
                return i;
            }
        }

        return -1;
    }
}
