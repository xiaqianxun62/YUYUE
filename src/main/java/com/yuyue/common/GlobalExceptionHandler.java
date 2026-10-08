package com.yuyue.common;

import com.yuyue.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * 全局异常处理：所有异常统一转为 ApiResponse
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBiz(BizException e) {
        return ApiResponse.fail(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String detail = fe == null ? "" : fe.getField() + " " + fe.getDefaultMessage();
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ApiResponse.fail(ErrorCode.PARAM_ERROR, "文件大小超过限制");
    }

    /**
     * 上传接口收到非 multipart/form-data 请求（如用 JSON/空 body 测试 /auth/avatar）：
     * 属于客户端用法错误，给 400 明确提示，而不是兜底成 500「系统繁忙」。
     * 注意：MaxUploadSizeExceededException 是本异常子类，已有更具体的 handler 优先匹配。
     */
    @ExceptionHandler(MultipartException.class)
    public ApiResponse<Void> handleMultipart(MultipartException e, HttpServletRequest request) {
        // 诊断：把 Spring Boot 实际收到的请求头/路径带回响应，
        // 用来区分「前端没正确发 multipart」还是「nginx 代理把 header/body 搞坏了」。
        String ct = request == null ? "(null)" : request.getContentType();
        String uri = request == null ? "(null)" : request.getRequestURI();
        int size = request == null ? -1 : request.getContentLength();
        log.warn("multipart 请求格式错误: uri={}, contentType='{}', contentLength={}, err={}",
                uri, ct, size, e.getMessage());
        String detail = "uri=" + uri + " contentType='" + ct + "' size=" + size
                + " err=" + e.getMessage();
        return ApiResponse.fail(ErrorCode.PARAM_ERROR,
                "上传必须使用 multipart/form-data，文件字段名为 file；诊断(" + detail + ")");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleOther(Exception e) {
        log.error("unexpected error", e);
        return ApiResponse.fail(ErrorCode.SYSTEM_ERROR);
    }
}
