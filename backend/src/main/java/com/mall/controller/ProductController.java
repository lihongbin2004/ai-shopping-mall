package com.mall.controller;

import com.mall.common.result.R;
import com.mall.entity.Product;
import com.mall.service.ProductService;
import com.mall.service.impl.RecommendServiceImpl;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/product")
@Api(tags = "商品接口")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired(required = false)
    private RecommendServiceImpl recommendService;

    @GetMapping("/page")
    @ApiOperation("分页查询商品")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(value = "size", required = false) Integer size,
                                       @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Long categoryId,
                                       @RequestParam(required = false) String sortBy) {
        int s = size != null ? size : (pageSize != null ? pageSize : 10);
        if (categoryId != null) {
            return R.ok(productService.getByCategoryId(categoryId, page, s, sortBy));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            return R.ok(productService.search(keyword.trim(), page, s, sortBy));
        }
        return R.ok(productService.getPage(page, s, sortBy));
    }

    @GetMapping("/detail/{id}")
    @ApiOperation("商品详情")
    public R<Product> detail(@PathVariable Long id,
                             @RequestParam(required = false) Long userId) {
        Product p = productService.getById(id);
        // 浏览埋点：累计到 Redis 兴趣画像
        if (p != null && userId != null && recommendService != null) {
            try { recommendService.trackView(userId, id); } catch (Exception ignore) {}
        }
        return R.ok(p);
    }

    @GetMapping("/search")
    @ApiOperation("搜索商品")
    public R<Map<String, Object>> search(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return R.ok(productService.search(keyword, page, size, null));
    }

    @GetMapping("/category/{categoryId}")
    @ApiOperation("按分类查询商品")
    public R<Map<String, Object>> byCategory(@PathVariable Long categoryId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        return R.ok(productService.getByCategoryId(categoryId, page, size, null));
    }

    @GetMapping("/hot")
    @ApiOperation("热销商品")
    public R<Map<String, Object>> hot(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "8") int size) {
        return R.ok(productService.getPage(page, size, "sales"));
    }

    @GetMapping("/new")
    @ApiOperation("新品上架")
    public R<Map<String, Object>> newProducts(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "8") int size) {
        return R.ok(productService.getPage(page, size, "newest"));
    }

    @GetMapping("/recommend")
    @ApiOperation("推荐商品")
    public R<Map<String, Object>> recommend(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "8") int size) {
        return R.ok(productService.getPage(page, size, null));
    }

    @PostMapping("/create")
    @ApiOperation("创建商品")
    public R<Product> create(@RequestBody Product product) {
        return R.ok(productService.create(product));
    }

    @PutMapping("/update")
    @ApiOperation("更新商品")
    public R<Product> update(@RequestBody Product product) {
        return R.ok(productService.update(product));
    }

    @DeleteMapping("/delete/{id}")
    @ApiOperation("删除商品")
    public R<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return R.ok();
    }
}
