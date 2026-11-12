package com.microservices.saga.orchestrator;

import com.microservices.saga.common.dto.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Shared lightweight RestTemplate stub for SAGA unit tests (tracks real state changes too). */
class FakeSagaRestTemplate extends RestTemplate {
    static final String ORDER = "http://order";
    static final String INV = "http://inv";
    static final String PAY = "http://pay";

    final List<String> calledUrls = new ArrayList<>();
    int availableStock = 5;
    int reservedStock = 0;
    BigDecimal productPrice = new BigDecimal("1000.00");
    BigDecimal walletBalance = new BigDecimal("5000.00");
    BigDecimal orderAmount = null;
    OrderStatus orderStatus = null;

    boolean paymentShouldSucceed = true;
    boolean inventoryShouldSucceed = true;
    int compensateFailuresRemaining = 0;

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables)
            throws RestClientException {
        calledUrls.add(url);
        if (url.startsWith(INV + "/api/inventory/")) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("productId", "PROD-002");
            map.put("availableStock", availableStock);
            map.put("reservedStock", reservedStock);
            map.put("price", productPrice);
            return (T) map;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables)
            throws RestClientException {
        calledUrls.add(url);

        if (url.equals(ORDER + "/api/order")) {
            orderStatus = OrderStatus.PENDING;
            OrderRequest or = (OrderRequest) request;
            orderAmount = or != null ? or.getAmount() : BigDecimal.TEN;
            return (T) new OrderResponse("ORD-1", "C", "P", 1, orderAmount, OrderStatus.PENDING, "created");
        }
        if (url.startsWith(INV + "/api/inventory/") && url.contains("/price?newPrice=")) {
            String priceStr = url.substring(url.indexOf("newPrice=") + "newPrice=".length());
            productPrice = new BigDecimal(priceStr);
            return (T) new InventoryResponse(null, "PROD-002", availableStock, true, "Price updated to $" + productPrice, productPrice);
        }
        if (url.startsWith(ORDER) && url.endsWith("/confirm")) {
            orderStatus = OrderStatus.COMPLETED;
            return (T) new OrderResponse("ORD-1", "C", "P", 1, BigDecimal.TEN, OrderStatus.COMPLETED, "confirmed");
        }
        if (url.startsWith(ORDER) && url.contains("/cancel")) {
            orderStatus = OrderStatus.CANCELLED;
            return (T) new OrderResponse("ORD-1", "C", "P", 1, BigDecimal.TEN, OrderStatus.CANCELLED, "cancelled");
        }
        if (url.startsWith(INV + "/api/inventory/reserve") || url.startsWith(INV + "/api/inventory/deduct")) {
            if (!inventoryShouldSucceed) {
                return (T) new InventoryResponse("ORD-1", "P", 1, false, "Out of stock");
            }
            availableStock -= 1;
            if (url.startsWith(INV + "/api/inventory/reserve")) {
                reservedStock += 1;
            }
            return (T) new InventoryResponse("ORD-1", "P", 1, true,
                    url.startsWith(INV + "/api/inventory/deduct") ? "Deducted" : "Reserved");
        }
        if (url.startsWith(INV + "/api/inventory/compensate")) {
            if (compensateFailuresRemaining > 0) {
                compensateFailuresRemaining--;
                throw new RestClientException("Transient network error during compensation");
            }
            reservedStock = Math.max(0, reservedStock - 1);
            availableStock += 1;
            return (T) new InventoryResponse("ORD-1", "P", 1, true, "Released");
        }
        if (url.startsWith(INV + "/api/inventory/confirm")) {
            reservedStock = Math.max(0, reservedStock - 1);
            return (T) new InventoryResponse("ORD-1", "P", 1, true, "Confirmed");
        }
        if (url.startsWith(INV + "/api/inventory/demo/compensate-failures")) {
            return (T) "armed";
        }
        if (url.startsWith(PAY + "/api/payment/debit")) {
            if (!paymentShouldSucceed) {
                return (T) new PaymentResponse("ORD-1", "C", BigDecimal.TEN, false, "Insufficient balance");
            }
            PaymentRequest pr = (PaymentRequest) request;
            walletBalance = walletBalance.subtract(pr.getAmount());
            return (T) new PaymentResponse("ORD-1", "C", pr.getAmount(), true, "Debited");
        }
        if (url.startsWith(PAY + "/api/payment/compensate")) {
            PaymentRequest pr = (PaymentRequest) request;
            walletBalance = walletBalance.add(pr.getAmount());
            return (T) new PaymentResponse("ORD-1", "C", pr.getAmount(), true, "Refunded");
        }
        return null;
    }

    boolean called(String part) {
        return calledUrls.stream().anyMatch(u -> u.contains(part));
    }

    int indexOf(String part) {
        for (int i = 0; i < calledUrls.size(); i++) {
            if (calledUrls.get(i).contains(part)) return i;
        }
        return -1;
    }
}
