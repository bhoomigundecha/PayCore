package com.bhoomi.paycore.common;

import com.bhoomi.paycore.ledger.AccountNotFoundException;
import com.bhoomi.paycore.ledger.InsufficientFundsException;
import com.bhoomi.paycore.ledger.LedgerConflictException;
import com.bhoomi.paycore.card.CardDeclinedException;
import com.bhoomi.paycore.ledger.LedgerException;
import com.bhoomi.paycore.stepup.WrongCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to RFC 9457 problem responses for every controller.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, AccountNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({InvalidStateException.class, LedgerConflictException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({InsufficientFundsException.class, IdempotencyKeyReusedException.class})
    public ProblemDetail handleUnprocessable(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(CardDeclinedException.class)
    public ProblemDetail handleDeclined(CardDeclinedException e) {
        ProblemDetail problem = problem(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
        problem.setTitle("Card declined");
        problem.setProperty("declineCode", e.code());
        return problem;
    }

    @ExceptionHandler(WrongCodeException.class)
    public ProblemDetail handleWrongCode(WrongCodeException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setProperty("attemptsLeft", e.attemptsLeft());
        return problem;
    }

    @ExceptionHandler(LedgerException.class)
    public ProblemDetail handleLedgerFailure(LedgerException e) {
        log.error("Ledger failure", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Ledger unavailable, please retry with the same Idempotency-Key");
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
