package com.ecm.order.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.common.response.ApiResponse;
import com.ecm.order.client.PromotionServiceClient;
import com.ecm.order.dto.request.ApplyDiscountRequest.DiscountCartItemRequest;
import com.ecm.order.dto.response.DiscountApplyResponse;
import com.ecm.order.dto.response.ItemDiscountApplyResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderDiscountServiceTest {

    private static final UUID VARIANT = UUID.randomUUID();
    private static final UUID DISCOUNT = UUID.randomUUID();
    private static final String TOKEN = "Bearer t";
    // one line of 2 x 500 = 1000
    private static final List<DiscountCartItemRequest> LINES = List.of(new DiscountCartItemRequest(VARIANT, 2, 500, UUID.randomUUID()));

    private final PromotionServiceClient client = mock(PromotionServiceClient.class);
    private final OrderDiscountService service = new OrderDiscountService(client);

    private void promotionAnswers(DiscountApplyResponse response) {
        when(client.apply(any(), anyString())).thenReturn(ApiResponse.success(response));
    }

    private static DiscountApplyResponse response(long orderDiscount, long itemDiscount) {
        return new DiscountApplyResponse(null, null, null, orderDiscount,
                List.of(new ItemDiscountApplyResponse(VARIANT, DISCOUNT, itemDiscount)));
    }

    @Test
    void aConsistentAnswerIsReturned() {
        DiscountApplyResponse answer = response(200, 300);
        promotionAnswers(answer);

        assertEquals(answer, service.apply("CODE", LINES, 1000, TOKEN));
    }

    @Test
    void aRejectedCodeBecomesABusinessError() {
        when(client.apply(any(), anyString())).thenThrow(mock(FeignException.BadRequest.class));

        assertThrows(BusinessException.class, () -> service.apply("BAD", LINES, 1000, TOKEN));
    }

    @Test
    void otherPromotionFailuresBecomeExternalServiceErrors() {
        when(client.apply(any(), anyString())).thenThrow(mock(FeignException.class));

        assertThrows(ExternalServiceException.class, () -> service.apply("CODE", LINES, 1000, TOKEN));
    }

    @Test
    void anEmptyAnswerIsRejected() {
        promotionAnswers(null);

        assertThrows(ExternalServiceException.class, () -> service.apply("CODE", LINES, 1000, TOKEN));
    }

    @Test
    void aLineDiscountedByMoreThanItCostsIsRejected() {
        promotionAnswers(response(0, 1001));

        assertThrows(ExternalServiceException.class, () -> service.apply("CODE", LINES, 1000, TOKEN));
    }

    @Test
    void aDiscountOnAVariantNotInTheCartIsRejected() {
        promotionAnswers(new DiscountApplyResponse(null, null, null, 0L,
                List.of(new ItemDiscountApplyResponse(UUID.randomUUID(), DISCOUNT, 10L))));

        assertThrows(ExternalServiceException.class, () -> service.apply("CODE", LINES, 1000, TOKEN));
    }

    @Test
    void anOrderDiscountLargerThanWhatIsLeftIsRejected() {
        promotionAnswers(response(701, 300));

        assertThrows(ExternalServiceException.class, () -> service.apply("CODE", LINES, 1000, TOKEN));
    }
}
