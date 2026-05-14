package com.chainreaction.ai;

import org.springframework.http.HttpStatus;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorCode;

public class StorySimilarityRejectionException extends ApiException {

    public StorySimilarityRejectionException() {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY,
                "AI response was too similar to previous word usage.");
    }
}
