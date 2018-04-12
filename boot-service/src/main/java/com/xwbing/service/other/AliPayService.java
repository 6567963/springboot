package com.xwbing.service.other;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.xwbing.domain.entity.pay.alipay.*;
import com.xwbing.exception.BusinessException;
import com.xwbing.exception.PayException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 说明: 支付宝支付接口实现
 * 项目名称: spring-demo
 * 创建时间: 2017/5/10 17:50
 * 作者:  xiangwb
 */
@Service
@PropertySource("classpath:pay.properties")
public class AliPayService {
    private final Logger logger = LoggerFactory.getLogger(AliPayService.class);
    /**
     * 支付宝分配给开发者的应用ID
     */
    @Value("${aliPay.appId}")
    private String appId;
    /**
     * 私钥
     */
    @Value("${aliPay.rsaPrivateKey}")
    private String privateKey;
    /**
     * 公钥
     */
    @Value("${aliPay.rsaPublicKey}")
    private String publicKey;
    /**
     * 请求url
     */
    @Value("${aliPay.requestUrl}")
    private String requestUrl;

    /**
     * 条形码扫码付
     *
     * @param param
     * @return
     */
    public AliPayBarCodePayResult barCodePay(AliPayBarCodePayParam param) {
        //设置条码支付
        param.setScene("bar_code");
        AliPayBarCodePayResult result = new AliPayBarCodePayResult(false);
        String checkArgument = checkArgument(param);
        if (StringUtils.isNotEmpty(checkArgument)) {
            result.setMessage(checkArgument);
            return result;
        }
        //获得初始化的aliPayClient
        AlipayClient alipayClient = new DefaultAlipayClient(requestUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA");
        //创建API对应的request类
        AlipayTradePayRequest request = new AlipayTradePayRequest();
        request.setBizContent(JSONObject.toJSONString(param));
        //通过aliPayClient调用API，获得对应的response类
        AlipayTradePayResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            logger.error(e.getMessage());
            throw new PayException("扫码支付异常");
        }
        //源码StringUtils.isEmpty(this.subCode)
        result.setSuccess(response.isSuccess());
        //根据response中的结果继续业务逻辑处理:subCode不为空，表示接口调用失败|code为10000，代表接口调用成功，subCode为空
        if (StringUtils.isEmpty(response.getSubCode())) {
            result.setCode(response.getCode());
            result.setMessage(response.getMsg());
        } else {
            result.setCode(response.getSubCode());
            result.setMessage(response.getSubMsg());
            logger.error(response.getSubMsg());
            return result;
        }
        result.setTradeNo(response.getTradeNo());
        result.setOutTradeNo(response.getOutTradeNo());
        result.setBuyerLogonId(response.getBuyerLogonId());
        result.setTotalAmount(response.getTotalAmount());
        result.setReceiptAmount(response.getReceiptAmount());
        result.setGmtPayment(response.getGmtPayment());
        result.setFundBillList(response.getFundBillList());
        result.setBuyerUserId(response.getBuyerUserId());
        result.setDiscountGoodsDetail(response.getDiscountGoodsDetail());
        logger.info("result = {}", result);
        return result;
    }

    /**
     * 退款
     *
     * @param param
     * @return
     */
    public AliPayRefundResult refund(AliPayRefundParam param) {
        AliPayRefundResult result = new AliPayRefundResult(false);
        AlipayClient alipayClient = new DefaultAlipayClient(requestUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent(JSONObject.toJSONString(param));
        AlipayTradeRefundResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            logger.error(e.getMessage());
            throw new PayException("退款异常");
        }
        result.setSuccess(response.isSuccess());
        if (StringUtils.isNotEmpty(response.getSubCode())) {
            logger.error(response.getSubMsg());
            result.setCode(response.getSubCode());
            result.setMessage(response.getSubMsg());
            return result;
        } else {
            result.setCode(response.getCode());
            result.setMessage(response.getMsg());
        }
        result.setTradeNo(response.getTradeNo());
        result.setOutTradeNo(response.getOutTradeNo());
        result.setBuyerLogonId(response.getBuyerLogonId());
        result.setFundChange(response.getFundChange());
        result.setRefundFee(response.getRefundFee());
        result.setGmtRefundPay(response.getGmtRefundPay());
        result.setBuyerUserId(response.getBuyerUserId());
        logger.info("result = {}", result);
        return result;
    }

    /**
     * 根据订单号 交易号查询 只需要一个即可
     * 如果isSuccess，根据tradeStatus，遍历AliPayTradeStatusEnum获取对应支付状态
     *
     * @param outTradeNo 订单号
     * @param tradeNo    交易号(推荐)
     * @return
     */
    public AliPayQueryResult queryOrder(String outTradeNo, String tradeNo) {
        if (StringUtils.isEmpty(outTradeNo) && StringUtils.isEmpty(tradeNo)) {
            throw new PayException("订单号和交易号不能同时为空!");
        }
        AliPayQueryResult result = new AliPayQueryResult(false);
        AlipayClient alipayClient = new DefaultAlipayClient(requestUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject jsonObject = new JSONObject();
        if (StringUtils.isNotEmpty(outTradeNo)) {
            jsonObject.put("out_trade_no", outTradeNo);
        }
        if (StringUtils.isNotEmpty(tradeNo)) {
            jsonObject.put("trade_no", tradeNo);
        }
        request.setBizContent(jsonObject.toString());
        AlipayTradeQueryResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            logger.error(e.getMessage());
            throw new PayException("查询订单异常");
        }
        result.setSuccess(response.isSuccess());
        //如果subCode存在,代表调用接口失败
        checkSubCode(result, response);
        //返回支付状态,业务调用时,遍历AliPayTradeStatusEnum与tradeStatus做比较
        result.setTradeStatus(response.getTradeStatus());
        return result;
    }

    /**
     * 退款查询  没有tradeStatus,isSuccess即为成功
     * 订单号和交易号2选1
     *
     * @param outTradeNo   订单号
     * @param tradeNo      交易号(推荐)
     * @param outRequestNo 退款请求号
     * @return
     */
    public AliPayQueryResult queryRefund(String outTradeNo, String tradeNo, String outRequestNo) {
        if (StringUtils.isEmpty(outTradeNo) && StringUtils.isEmpty(tradeNo)) {
            throw new PayException("订单号和交易号不能同时为空!");
        }
        AliPayQueryResult result = new AliPayQueryResult(false);
        AlipayClient alipayClient = new DefaultAlipayClient(requestUrl, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("out_request_no", outRequestNo);
        if (StringUtils.isNotEmpty(outTradeNo)) {
            jsonObject.put("out_trade_no", outTradeNo);
        }
        if (StringUtils.isNotEmpty(tradeNo)) {
            jsonObject.put("trade_no", tradeNo);
        }
        request.setBizContent(jsonObject.toString());
        AlipayTradeQueryResponse response;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            logger.error(e.getMessage());
            throw new PayException("退款查询异常");
        }
        result.setSuccess(response.isSuccess());
        //如果subCode存在,代表调用接口失败
        checkSubCode(result, response);
        return result;
    }

    /**
     * 查询接口校验subCode是否存在。如果subCode存在，代表调用接口失败。业务调用时只需判断result是否为success即可
     *
     * @return
     */
    private void checkSubCode(AliPayQueryResult result, AlipayTradeQueryResponse response) {
        if (StringUtils.isNotEmpty(response.getSubCode())) {
            result.setCode(response.getSubCode());
            result.setMessage(response.getSubMsg());
            logger.error(response.getSubMsg());
        } else {
            result.setCode(response.getCode());
            result.setMessage(response.getMsg());
        }
    }

    /**
     * 入参校验
     *
     * @return
     */
    private String checkArgument(AliPayBarCodePayParam param) {
        String message;
        if (StringUtils.isEmpty(param.getOutTradeNo())) {
            message = "订单号为空";
        } else if (StringUtils.isEmpty(param.getAuthCode())) {
            message = "授权码为空";
        } else if (StringUtils.isEmpty(param.getSubject())) {
            message = "产品名称为空";
        } else if (0 >= param.getTotalAmount()) {
            message = "金额必须大于0";
        } else if (StringUtils.isEmpty(param.getScene())) {
            message = "支付方式不能为空";
        } else {
            message = StringUtils.EMPTY;
        }
        return message;
    }

    public static void main(String[] args) {
        //刷卡支付
        AliPayService alipayBuilder = new AliPayService();
        String orderNo = "201705180202";
//        String authCode = "286796667181427987";
//        AlipayBarCodePayParam codePayParam = new AlipayBarCodePayParam(orderNo, authCode, "test", 0.1f);
//        AlipayBarCodePayResult codePayResult = alipayBuilder.barCodePay(codePayParam);
//        System.out.println(codePayResult.isSuccess() + codePayResult.getMessage());

        //查询订单
        String tradeNo = "2017051221001004630289850336";
        AliPayQueryResult queryResult = alipayBuilder.queryOrder(orderNo, tradeNo);
        if (!queryResult.isSuccess()) {
            throw new BusinessException(queryResult.getMessage());
        }
        String tradeStatus = queryResult.getTradeStatus();
        for (AliPayTradeStatusEnum status : AliPayTradeStatusEnum.values()) {
            if (Objects.equals(tradeStatus, status.getCode())) {
                System.out.println(status.getName());
                break;
            }
        }

//        //退款操作
        AliPayRefundParam refundParam = new AliPayRefundParam("201505129999", orderNo, 0.05f, "test");
//        AlipayRefundResult refundResult = alipayBuilder.refund(refundParam);
//        System.out.println(refundResult.getMessage());

//        //查询退款 只要success,即为成功
//        AlipayQueryResult refund = alipayBuilder.queryRefund(orderNo, "", "201505129999");
//        System.out.println(refund.isSuccess());
    }
}