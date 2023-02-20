package com.example.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.example.dao.SeatInfoDao;
import com.example.entity.Account;
import com.example.entity.SeatInfo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class SeatInfoService {

    @Resource
    private SeatInfoDao seatInfoDao;

    public SeatInfo findDetail(Long goodsId) {
        List<SeatInfo> detail = seatInfoDao.findDetail(goodsId);
        if (!CollectionUtil.isEmpty(detail)) {
            StringBuilder positon = new StringBuilder();
            for (SeatInfo info : detail) {
                positon.append(info.getPosition()).append(",");
            }
            SeatInfo seatInfo = new SeatInfo();
            seatInfo.setGoodsId(goodsId);
            seatInfo.setPosition("[" + positon.substring(0, positon.length() - 1) + "]");
            return seatInfo;
        } else {
            return new SeatInfo();
        }
    }

    public void save(SeatInfo seatInfo, Account account) {
        // 把座位信息拆开
        if (!StrUtil.isEmpty(seatInfo.getPosition())) {
            String substring = seatInfo.getPosition().substring(1, seatInfo.getPosition().length() - 1);
            String[] positons = substring.split(",");
            // 将座位信息拆开入库
            for (String positon : positons) {
                SeatInfo info = new SeatInfo();
                info.setGoodsId(seatInfo.getGoodsId());
                info.setUserId(account.getId());
                info.setLevel(account.getLevel());
                info.setPosition(positon);
                info.setOrderid(seatInfo.getOrderid());

                seatInfoDao.insertSelective(info);
            }
        }
    }
}
