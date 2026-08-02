package com.workshop.loanservice.api.v2;

import com.workshop.loanservice.api.v2.dto.BorrowerV2Dto;
import com.workshop.loanservice.api.v2.dto.LoanV2Dto;
import com.workshop.loanservice.api.v2.dto.PaymentV2Dto;
import com.workshop.loanservice.api.v2.dto.SliceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 read API: everything v1 could not become without breaking its clients - bounded pages, a
 * keyset cursor, ISO-8601 dates and canonical codes.
 *
 * <p>v1 remains untouched and unbounded at {@code /api/*}. This is the migration path for clients
 * that cannot keep pulling half a million rows in one response.
 */
@RestController
@RequestMapping("/api/v2")
public class LoanV2Controller {

    private final LoanV2Service service;

    public LoanV2Controller(LoanV2Service service) {
        this.service = service;
    }

    /**
     * @param afterId keyset cursor; when present, {@code page} and {@code sort} are ignored and the
     *                page is returned in id order. This is the form to use for deep pagination.
     * @param count   opt-in {@code COUNT(*)}, off by default because it costs as much as the page
     */
    @GetMapping("/loans")
    public SliceResponse<LoanV2Dto> loans(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(required = false) Long afterId,
                                          @RequestParam(defaultValue = "false") boolean count) {
        return service.loans(page, size, sort, afterId, count);
    }

    @GetMapping("/loans/{accountNumber}")
    public LoanV2Dto loan(@PathVariable String accountNumber) {
        return service.loan(accountNumber);
    }

    @GetMapping("/loans/{accountNumber}/payments")
    public SliceResponse<PaymentV2Dto> payments(@PathVariable String accountNumber,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return service.paymentsForLoan(accountNumber, page, size);
    }

    @GetMapping("/borrowers")
    public SliceResponse<BorrowerV2Dto> borrowers(@RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) Long afterId,
                                                  @RequestParam(defaultValue = "false") boolean count) {
        return service.borrowerPage(page, size, sort, afterId, count);
    }

    @GetMapping("/borrowers/{externalId}")
    public BorrowerV2Dto borrower(@PathVariable String externalId) {
        return service.borrower(externalId);
    }
}
