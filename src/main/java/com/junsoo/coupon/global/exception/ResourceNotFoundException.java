package com.junsoo.coupon.global.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName + " not found. id=" + id);
    }
}
