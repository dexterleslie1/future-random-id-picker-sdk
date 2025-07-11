package com.future.random.id.picker;

import com.future.common.exception.BusinessException;
import com.future.common.http.ListResponse;
import com.future.common.http.ObjectResponse;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

import java.util.List;

public interface Api {
    @RequestLine("POST /api/v1/id/picker/init?flag={flag}")
    ObjectResponse<String> init(@Param("flag") String flag) throws BusinessException;

    @RequestLine("PUT /api/v1/id/picker/reset?flag={flag}")
    ObjectResponse<String> reset(@Param("flag") String flag) throws BusinessException;

    @RequestLine("POST /api/v1/id/picker/addIdList")
    @Headers(value = {"Content-Type: application/x-www-form-urlencoded"})
    ObjectResponse<String> addIdList(@Param("flag") String flag,
                                     @Param("idList") List<Long> idList) throws BusinessException;

    @RequestLine("GET /api/v1/id/picker/listIdRandomly?flag={flag}&size={size}")
    ListResponse<Long> listIdRandomly(@Param("flag") String flag,
                                      @Param("size") int size) throws BusinessException;
}
