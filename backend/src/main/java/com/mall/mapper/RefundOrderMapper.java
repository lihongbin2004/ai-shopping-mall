package com.mall.mapper;

import com.mall.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface RefundOrderMapper {
    int insert(RefundOrder refund);
    RefundOrder selectById(@Param("id") Long id);
    RefundOrder selectByRefundNo(@Param("refundNo") String refundNo);
    List<RefundOrder> selectByUserId(@Param("userId") Long userId);
    List<RefundOrder> selectAll(@Param("params") Map<String, Object> params);
    int countAll(@Param("params") Map<String, Object> params);
    int updateAudit(@Param("id") Long id, @Param("status") Integer status,
                    @Param("rejectReason") String rejectReason,
                    @Param("adminReply") String adminReply);
    int updateRefund(@Param("id") Long id, @Param("refundTime") Date refundTime);
}
