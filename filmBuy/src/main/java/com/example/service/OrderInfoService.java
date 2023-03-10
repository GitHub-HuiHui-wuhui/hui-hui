package com.example.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.example.common.ResultCode;
import com.example.dao.OrderGoodsRelDao;
import com.example.dao.OrderInfoDao;
import com.example.dao.SeatInfoDao;
import com.example.entity.*;
import com.example.exception.CustomException;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class OrderInfoService {

    @Resource
    private OrderInfoDao orderInfoDao;
    @Resource
    private GoodsInfoService goodsInfoService;
	@Resource
	private AdminInfoService adminInfoService;
	@Resource
	private UserInfoService userInfoService;

    @Resource
    private OrderGoodsRelDao orderGoodsRelDao;
    @Resource
    private CartInfoService cartInfoService;
    @Resource
    private SeatInfoDao seatInfoDao;


    /**
     * 根据id查询订单信息
     */
    public OrderInfo findById(Long id) {
        OrderInfo orderInfo = orderInfoDao.selectByPrimaryKey(id);
        packOrder(orderInfo);
        return orderInfo;
    }

    public List<OrderInfo> findAll(Long userId, Integer level) {
        List<OrderInfo> orderInfos = orderInfoDao.findByUserId(userId, level);
        for (OrderInfo orderInfo : orderInfos) {
            packOrder(orderInfo);
        }
        return orderInfos;
    }

    /**
     * 包装订单的用户和商品信息
     */
    private void packOrder(OrderInfo orderInfo) {
        Long orderId = orderInfo.getId();
        List<OrderGoodsRel> rels = orderGoodsRelDao.findByOrderId(orderId);
        orderInfo.setUserInfo(getUserInfo(orderInfo.getUserId(), orderInfo.getLevel()));
        List<GoodsInfo> goodsList = CollUtil.newArrayList();
        orderInfo.setGoodsList(goodsList);
        for (OrderGoodsRel rel : rels) {
            GoodsInfo goodsDetailInfo = goodsInfoService.findById(rel.getGoodsId());
            if (goodsDetailInfo != null) {
                // 注意这里返回的count是用户加入商品的数量，而不是商品的库存
                goodsDetailInfo.setNum(rel.getCount());
                goodsList.add(goodsDetailInfo);
            }
        }
    }

    /**
     * 分页查询订单信息
     */
    public PageInfo<OrderInfo> findPages(Long userId, Integer pageNum, Integer pageSize, HttpServletRequest request) {
        Account user = (Account) request.getSession().getAttribute("user");
        if (user == null) {
            throw new CustomException("1001", "session已失效，请重新登录");
        }
        Integer level = user.getLevel();
        PageHelper.startPage(pageNum, pageSize);
        List<OrderInfo> orderInfos;
        if (1 == level) {
            orderInfos = orderInfoDao.selectAll();
        } else if (userId != null){
            orderInfos = orderInfoDao.findByEndUserId(userId, null, level);
        } else {
            orderInfos = new ArrayList<>();
        }
        for (OrderInfo orderInfo : orderInfos) {
            packOrder(orderInfo);
        }
        return PageInfo.of(orderInfos);
    }

    public PageInfo<OrderInfo> findFrontPages(Long userId, Integer level, String status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<OrderInfo> orderInfos;
        if (userId != null){
            orderInfos = orderInfoDao.findByEndUserId(userId, status, level);
        } else {
            orderInfos = new ArrayList<>();
        }
        for (OrderInfo orderInfo : orderInfos) {
            packOrder(orderInfo);
        }
        return PageInfo.of(orderInfos);
    }

    /**
     * 分页查询订单信息
     */
    public PageInfo<OrderInfo> findPages(Long userId, Integer level, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<OrderInfo> orderInfos;
        if (userId != null) {
            orderInfos = orderInfoDao.findByUserId(userId, level);
        } else {
            orderInfos = orderInfoDao.selectAll();
        }
        for (OrderInfo orderInfo : orderInfos) {
            packOrder(orderInfo);
        }
        return PageInfo.of(orderInfos);
    }

    /**
     * 下单
     */
    @Transactional
    public OrderInfo add(OrderInfo orderInfo) {
        Long userId = orderInfo.getUserId();
        Integer level = orderInfo.getLevel();
        // 订单id：用户id + 当前年月日时分 + 4位流水号
        String orderId = userId + DateUtil.format(new Date(), "yyyyMMddHHmm") + RandomUtil.randomNumbers(4);
        orderInfo.setOrderId(orderId);

        Account userInfo = getUserInfo(userId, level);
        orderInfo.setLinkAddress(userInfo.getAddress());
        orderInfo.setLinkMan(userInfo.getNickName());
        orderInfo.setLinkPhone(userInfo.getPhone());
        orderInfo.setCreateTime(DateUtil.formatDateTime(new Date()));
        // 生成订单
        orderInfoDao.insertSelective(orderInfo);

        List<GoodsInfo> goodsList = orderInfo.getGoodsList();
        for (GoodsInfo orderGoodsVO : goodsList) {
            Long goodsId = orderGoodsVO.getId();
            // 查询商品信息
            GoodsInfo goodsDetail = goodsInfoService.findById(goodsId);
            if (goodsDetail != null) {
                Integer orderCount = orderInfo.getTotal();
                // 销量 +count
                int sales = goodsDetail.getSales() == null ? 0 : goodsDetail.getSales();
                goodsDetail.setSales(sales + orderCount);
                goodsInfoService.update(goodsDetail);

                // 建立关系
                OrderGoodsRel orderGoodsRel = new OrderGoodsRel();
                orderGoodsRel.setGoodsId(goodsId);
                orderGoodsRel.setOrderId(orderInfo.getId());
                orderGoodsRel.setCount(orderCount);
                orderGoodsRelDao.insertSelective(orderGoodsRel);
            }
        }
        // 更新订单信息
        orderInfoDao.updateByPrimaryKeySelective(orderInfo);

        // 下单 清空购物车
        cartInfoService.empty(userId, level);
        return orderInfo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Integer level, List<GoodsInfo> goodsList) {

        Account userInfo = getUserInfo(userId, level);

        for (GoodsInfo orderGoodsVO : goodsList) {
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setLinkAddress(userInfo.getAddress());
            orderInfo.setLinkMan(userInfo.getNickName());
            orderInfo.setLinkPhone(userInfo.getPhone());
            orderInfo.setCreateTime(DateUtil.formatDateTime(new Date()));
            orderInfo.setUserId(userId);
            orderInfo.setStatus("待付款");

            // 订单id：用户id + 当前年月日时分 + 4位流水号
            String orderId = userId + DateUtil.format(new Date(), "yyyyMMddHHmm") + RandomUtil.randomNumbers(4);
            orderInfo.setOrderId(orderId);

            Long goodsId = orderGoodsVO.getId();
            // 查询商品信息
            GoodsInfo goodsDetail = goodsInfoService.findById(goodsId);
            if (goodsDetail != null) {
                Integer orderCount = orderInfo.getTotal();
                // 销量 +count
                int sales = goodsDetail.getSales() == null ? 0 : goodsDetail.getSales();
                goodsDetail.setSales(sales + orderCount);
                goodsInfoService.update(goodsDetail);

                // 建立关系
                OrderGoodsRel orderGoodsRel = new OrderGoodsRel();
                orderGoodsRel.setGoodsId(goodsId);
                orderGoodsRel.setOrderId(orderInfo.getId());
                orderGoodsRel.setCount(orderCount);
                orderGoodsRelDao.insertSelective(orderGoodsRel);
            }
        }

        // 下单 清空购物车
        cartInfoService.empty(userId, level);
    }

    @Transactional
    public void delete(Long id) {
        orderInfoDao.deleteById(id);
        orderGoodsRelDao.deleteByOrderId(id);
    }

    public void deleteGoods(Long goodsId, Long orderId) {
        orderGoodsRelDao.deleteByGoodsIdAndOrderId(goodsId, orderId);
    }

    public OrderInfo findByOrderId(Long orderId) {
        return orderInfoDao.findById(orderId);
    }

    public void changeStatus(Long id, String status) {
        OrderInfo order = orderInfoDao.findById(id);
        Long userId = order.getUserId();
        Integer level = order.getLevel();
		if (level == 1) {
			AdminInfo user = adminInfoService.findById(userId);
			if ("待观看".equals(status)) {
				Double account = user.getAccount();
				Double totalPrice = order.getTotalPrice();
				if(account < totalPrice) {
					throw new CustomException("-1", "账户余额不足");
				}
				user.setAccount(user.getAccount() - order.getTotalPrice());
				adminInfoService.update(user);
			}
			if ("已退票".equals(status)) {
                // 1、首先查到该订单对应的电影详情
                GoodsInfo goodsInfo = orderInfoDao.findGoodsByOrderId(order.getOrderId());
                // 2、获取当前时间，如果当前时间已经超过放映时间即不允许退票，否则可以退票
                long now = System.currentTimeMillis();
                try {
                    long date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(goodsInfo.getBeginTime()).getTime();
                    if (now >= date) {
                        throw new CustomException("1001", "电影已放映，无法退票");
                    } else {
                        // 3、退票成功，款原路返回到买家账户余额
                        user.setAccount(user.getAccount() + order.getTotalPrice());
                        adminInfoService.update(user);
                        // 4、释放该买家选的座位
                        seatInfoDao.deleteByUserIdAndOrderId(userId, level, order.getOrderId());
                    }
                } catch (ParseException e) {
                    throw new CustomException("1001", "时间转换失败");
                }
			}
            if ("已取消".equals(status)) {
                // 1、首先查到该订单对应的电影详情
                GoodsInfo goodsInfo = orderInfoDao.findGoodsByOrderId(order.getOrderId());
                // 2、释放该买家选的座位
                seatInfoDao.deleteByUserIdAndOrderId(userId, level, order.getOrderId());
            }
			if ("完成".equals(status)) {
			    // 返回积分，换算成余额返回到用户账号余额里
                // 每看一个电影赠送10积分，相当于0.1元
                user.setAccount(user.getAccount() + 0.1D);
                adminInfoService.update(user);
            }
			orderInfoDao.updateStatus(id, status);
		}
		if (level == 2) {
			UserInfo user = userInfoService.findById(userId);
			if ("待观看".equals(status)) {
				Double account = user.getAccount();
				Double totalPrice = order.getTotalPrice();
				if(account < totalPrice) {
					throw new CustomException("-1", "账户余额不足");
				}
				user.setAccount(user.getAccount() - order.getTotalPrice());
				userInfoService.update(user);
			}
			if ("已退票".equals(status)) {
                // 1、首先查到该订单对应的电影详情
                GoodsInfo goodsInfo = orderInfoDao.findGoodsByOrderId(order.getOrderId());
                // 2、获取当前时间，如果当前时间已经超过放映时间即不允许退票，否则可以退票
                long now = System.currentTimeMillis();
                try {
                    long date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(goodsInfo.getBeginTime()).getTime();
                    if (now >= date) {
                        throw new CustomException("1001", "电影已放映，无法退票");
                    } else {
                        // 3、退票成功，款原路返回到买家账户余额
                        user.setAccount(user.getAccount() + order.getTotalPrice());
                        userInfoService.update(user);
                        // 4、释放该买家选的座位
                        seatInfoDao.deleteByUserIdAndOrderId(userId, level, order.getOrderId());
                    }
                } catch (ParseException e) {
                    throw new CustomException("1001", "时间转换失败");
                }
			}
			if ("已取消".equals(status)) {
                // 1、首先查到该订单对应的电影详情
                GoodsInfo goodsInfo = orderInfoDao.findGoodsByOrderId(order.getOrderId());
                // 2、释放该买家选的座位
                seatInfoDao.deleteByUserIdAndOrderId(userId, level, order.getOrderId());
            }
            if ("完成".equals(status)) {
                // 返回积分，换算成余额返回到用户账号余额里
                // 每看一个电影赠送10积分，相当于0.01元
                user.setAccount(user.getAccount() + 0.1D);
                userInfoService.update(user);
            }
			orderInfoDao.updateStatus(id, status);
		}

    }

    private Account getUserInfo(Long userId, Integer level) {
        Account account = new Account();
		if (level == 1) {
			account = adminInfoService.findById(userId);
		}
		if (level == 2) {
			account = userInfoService.findById(userId);
		}

        return account;
    }
}
