package ru.practicum.request.handler.exception;

import org.slf4j.Logger;

public class DuplicatedDataException extends RuntimeException {
    public DuplicatedDataException(String message) {
        super(message);
    }

    public DuplicatedDataException(String message, Logger logger) {
        this(message);
        logger.error(message);
    }
}
