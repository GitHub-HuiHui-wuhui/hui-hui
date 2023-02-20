package com.example.dao;

import com.example.entity.SeatInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface SeatInfoDao extends Mapper<SeatInfo> {
    @Select("select * from seat_info where goodsId = #{goodsId}")
    List<SeatInfo> findDetail(Long goodsId);

    @Delete("delete from seat_info where userId = #{userId} and level = #{level} and orderid = #{orderId}")
    void deleteByUserIdAndOrderId(Long userId, Integer level, String orderId);
}
