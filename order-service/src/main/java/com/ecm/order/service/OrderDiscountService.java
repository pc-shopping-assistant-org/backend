package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.order.client.PromotionServiceClient;
import com.ecm.order.dto.request.ApplyDiscountRequest;
import com.ecm.order.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.dto.response.ItemDiscountApplyResponse;
import com.ecm.order.exception.OrderErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asks the Promotion Service what a cart is discounted by, and checks the answer before the order trusts it:
 * the amounts must be consistent and no line may be discounted by more than it costs.
 */
@Component
@RequiredArgsConstructor
public class OrderDiscountService {

    private static final String PROMOTION_SERVICE = "promotion-service";

    private final PromotionServiceClient promotionServiceClient;

    public DiscountApplyResponse apply(String code, List<DiscountCartItemRequest> lines, long grossSubtotal, String bearerToken) {
        // 1. Ask the Promotion Service; a 400 means it rejected the code or the cart
        DiscountApplyResponse response;
        try {
            response = promotionServiceClient.apply(new ApplyDiscountRequest(code, grossSubtotal, lines), bearerToken).getData();
        } catch (FeignException.BadRequest ex) {
            throw new BusinessException(OrderErrorCode.DISCOUNT_NOT_APPLICABLE);
        } catch (FeignException ex) {
            throw new ExternalServiceException(PROMOTION_SERVICE, ex);
        }
        if (response == null || response.itemDiscounts() == null || response.orderDiscountAmount() == null) {
            throw new ExternalServiceException(PROMOTION_SERVICE, "Invalid discount response");
        }

        // 2. No line is discounted by more than it costs, and the totals add up
        Map<UUID, Long> lineAmounts = new HashMap<>();
        lines.forEach(line -> lineAmounts.put(line.productVariantId(), line.unitPrice() * line.quantity()));
        long itemDiscountTotal = 0;
        for (ItemDiscountApplyResponse itemDiscount : response.itemDiscounts()) {
            Long lineAmount = lineAmounts.get(itemDiscount.productVariantId());
            if (lineAmount == null || itemDiscount.discountAmount() == null || itemDiscount.discountAmount() < 0
                    || itemDiscount.discountAmount() > lineAmount || itemDiscount.discountId() == null) {
                throw new ExternalServiceException(PROMOTION_SERVICE, "Invalid item discount");
            }
            itemDiscountTotal += itemDiscount.discountAmount();
        }
        if (response.orderDiscountAmount() < 0 || response.orderDiscountAmount() > grossSubtotal - itemDiscountTotal) {
            throw new ExternalServiceException(PROMOTION_SERVICE, "Invalid order discount");
        }
        return response;
    }
}
