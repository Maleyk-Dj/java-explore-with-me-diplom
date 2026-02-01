package ru.practicum.handler;


import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.practicum.dto.error.ApiError;
import ru.practicum.handler.exception.ConflictException;
import ru.practicum.handler.exception.DuplicatedDataException;
import ru.practicum.handler.exception.NotFoundException;
import ru.practicum.handler.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static ru.practicum.util.Constants.PATTERN_FORMATE_DATE;

@RestControllerAdvice
public class ErrorHandlingControllerAdvice {
    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingControllerAdvice.class);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern(PATTERN_FORMATE_DATE);

    private ApiError api(HttpStatus status, String reason, String message, List<String> errors) {
        return new ApiError(
                errors == null ? List.of() : errors,
                message,
                reason,
                status.name(),
                LocalDateTime.now().format(FMT)
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onConstraintValidationException(ConstraintViolationException e) {
        log.warn("400 {}", e.getMessage());
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(fe -> "Field: %s. Error: %s. Value: %s"
                        .formatted(fe.getPropertyPath().toString(), fe.getMessage(), fe.getInvalidValue()))
                .orElse("Validation failed");
        return api(HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                message,
                null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // проверяем, наличие ошибок при валидации значений в полях объекта
        log.warn("400 {}", e.getMessage());
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> "Field: %s. Error: %s. Value: %s"
                        .formatted(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .orElse("Validation failed");
        return api(HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                message,
                null);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError onNotFoundException(NotFoundException e) {
        log.warn("404 {}", e.getMessage());

        return api(HttpStatus.NOT_FOUND,
                "The required object was not found.",
                e.getMessage(),
                null);
    }

    @ExceptionHandler(DuplicatedDataException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onDuplicatedDataException(DuplicatedDataException e) {
        log.warn("409 {}", e.getMessage());

        return api(HttpStatus.CONFLICT,
                "Duplication of an object.",
                e.getMessage(),
                null);
    }


    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onValidationException(ValidationException e) {
        log.warn("400 {}", e.getMessage());

        return api(HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                e.getMessage(), // Сообщение, например, "Event date cannot be in the past"
                null);
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onConflictException(ConflictException e) {
        log.warn("409 {}", e.getMessage());

        return api(HttpStatus.CONFLICT,
                "For the requested operation the conditions are not met.",
                e.getMessage(),
                null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {

        log.warn("400 {}", e.getMessage());

        return api(
                HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                "Parameter '%s' has invalid value '%s'"
                        .formatted(e.getName(), e.getValue()),
                null
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onMissingServletRequestParameterException(
            MissingServletRequestParameterException e) {

        log.warn("400 Missing request parameter: {}", e.getParameterName());

        String message = "Required request parameter '%s' is missing"
                .formatted(e.getParameterName());

        return api(
                HttpStatus.BAD_REQUEST,
                "Incorrectly made request.",
                message,
                null
        );
    }

}