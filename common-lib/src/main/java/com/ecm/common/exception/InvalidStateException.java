package com.ecm.common.exception;

/**
 * A state-machine violation: the requested operation cannot be performed given the
 * resource's current state (e.g. cancelling an order that has already shipped, refunding
 * a payment that was never captured). The shape of the error is identical across
 * services; only the {@link ErrorCode} and state/action names differ, so services should
 * still supply their own {@link ErrorCode} when the specific code matters to API
 * consumers, falling back to {@link CommonErrorCode#INVALID_STATE} otherwise.
 */
public class InvalidStateException extends BusinessException {

    public InvalidStateException(String message) {
        super(CommonErrorCode.INVALID_STATE, message);
    }

    public InvalidStateException(String resourceName, Object identifier, String currentState, String attemptedAction) {
        super(CommonErrorCode.INVALID_STATE, buildMessage(resourceName, identifier, currentState, attemptedAction));
    }

    public InvalidStateException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidStateException(ErrorCode errorCode, String resourceName, Object identifier, String currentState,
                                 String attemptedAction) {
        super(errorCode, buildMessage(resourceName, identifier, currentState, attemptedAction));
    }

    private static String buildMessage(String resourceName, Object identifier, String currentState, String attemptedAction) {
        return "Cannot " + attemptedAction + " " + resourceName + " '" + identifier + "' in state " + currentState;
    }
}
