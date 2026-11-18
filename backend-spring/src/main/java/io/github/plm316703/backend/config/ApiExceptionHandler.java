package io.github.plm316703.backend.config;

import io.github.plm316703.backend.auth.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 서비스에서 던진 예외를 화면이 이해할 수 있는 응답으로 바꾼다 (프로젝트 규칙).
 *  - IllegalArgumentException (잘못된 요청)        → 400
 *  - IllegalStateException   (지금 상태로는 불가능) → 409
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        // DB unique 제약 위반 (예: 동시에 같은 배정 두 번) → 원문 SQL 오류는 숨김
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "이미 존재하는 데이터입니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "입력값을 확인하세요."));
    }

    @ExceptionHandler(CurrentUser.UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> unauthorized(CurrentUser.UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }
}
