package com.workshop.loanservice.api.v2;

import com.workshop.loanservice.api.v2.dto.BorrowerV2Dto;
import com.workshop.loanservice.api.v2.dto.LoanV2Dto;
import com.workshop.loanservice.api.v2.dto.PaymentV2Dto;
import com.workshop.loanservice.api.v2.dto.SliceResponse;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.Payment;
import com.workshop.loanservice.modern.repository.BorrowerRepository;
import com.workshop.loanservice.modern.repository.LoanAccountRepository;
import com.workshop.loanservice.modern.repository.PaymentRepository;
import com.workshop.loanservice.service.LoanNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * v2 read API, served only from the modern schema.
 *
 * <p>v2 exists because v1 cannot be fixed: its contract promises the entire table in one response.
 * Everything here is bounded, and the cursor form ({@code afterId}) is the one that stays flat at
 * 500k rows.
 */
@Service
@Transactional(transactionManager = "modernTransactionManager", readOnly = true)
public class LoanV2Service {

    private final LoanAccountRepository accounts;
    private final BorrowerRepository borrowers;
    private final PaymentRepository payments;

    public LoanV2Service(LoanAccountRepository accounts, BorrowerRepository borrowers, PaymentRepository payments) {
        this.accounts = accounts;
        this.borrowers = borrowers;
        this.payments = payments;
    }

    public SliceResponse<LoanV2Dto> loans(Integer page, Integer size, String sort, Long afterId, boolean count) {
        Long total = count ? accounts.count() : null;
        if (afterId != null) {
            int limit = PageRequests.clampSize(size);
            List<LoanAccount> rows = accounts.findAfterId(afterId, PageRequest.ofSize(limit));
            return keyset(rows, limit, LoanV2Service::toLoanDto, LoanAccount::getId, total);
        }
        Slice<LoanAccount> slice = accounts.findAllBy(PageRequests.of(page, size, sort, PageRequests.LOAN_SORT));
        return offset(slice, LoanV2Service::toLoanDto, total);
    }

    public LoanV2Dto loan(String accountNumber) {
        return accounts.findByAccountNumberWithBorrowerAndProduct(accountNumber)
            .map(LoanV2Service::toLoanDto)
            .orElseThrow(() -> new LoanNotFoundException("loan"));
    }

    public SliceResponse<BorrowerV2Dto> borrowerPage(Integer page, Integer size, String sort, Long afterId,
                                                     boolean count) {
        Long total = count ? borrowers.count() : null;
        if (afterId != null) {
            int limit = PageRequests.clampSize(size);
            List<Borrower> rows = borrowers.findAfterId(afterId, PageRequest.ofSize(limit));
            return keyset(rows, limit, LoanV2Service::toBorrowerDto, Borrower::getId, total);
        }
        Slice<Borrower> slice = borrowers.findAllBy(PageRequests.of(page, size, sort, PageRequests.BORROWER_SORT));
        return offset(slice, LoanV2Service::toBorrowerDto, total);
    }

    public BorrowerV2Dto borrower(String externalId) {
        return borrowers.findByExternalId(externalId)
            .map(LoanV2Service::toBorrowerDto)
            .orElseThrow(() -> new LoanNotFoundException("borrower"));
    }

    public SliceResponse<PaymentV2Dto> paymentsForLoan(String accountNumber, Integer page, Integer size) {
        // Unsorted Pageable on purpose: the query already fixes the order (newest first), and a
        // Pageable sort would be appended to it and fight the ORDER BY that clients depend on.
        Slice<Payment> slice = payments.findByAccountNumberOrderByDateDesc(accountNumber,
            PageRequest.of(page == null ? 0 : page, PageRequests.clampSize(size)));
        return offset(slice, LoanV2Service::toPaymentDto, null);
    }

    /**
     * A page shorter than the requested limit is the last one, so no extra count and no extra probe
     * query is needed to answer {@code hasNext}.
     */
    private static <E, D> SliceResponse<D> keyset(List<E> rows, int limit, Function<E, D> mapper,
                                                  Function<E, Long> id, Long total) {
        boolean hasNext = rows.size() == limit;
        Long next = rows.isEmpty() ? null : id.apply(rows.get(rows.size() - 1));
        return SliceResponse.of(rows.stream().map(mapper).toList(), hasNext, next, total);
    }

    private static <E, D> SliceResponse<D> offset(Slice<E> slice, Function<E, D> mapper, Long total) {
        return SliceResponse.of(slice.getContent().stream().map(mapper).toList(), slice.hasNext(), null, total);
    }

    private static LoanV2Dto toLoanDto(LoanAccount a) {
        Borrower b = a.getBorrower();
        return new LoanV2Dto(a.getId(), a.getAccountNumber(), b.getExternalId(), b.getFirstName(), b.getLastName(),
            a.getProduct().getCode(), a.getProduct().getName(), a.getOriginalAmount(), a.getCurrentBalance(),
            a.getInterestRate(), a.getTermMonths(), a.getMonthlyPayment(), a.getOriginationDate(),
            a.getMaturityDate(), a.getNextPaymentDate(), a.getStatus(), a.getDelinquencyDays(),
            a.getEscrowBalance(), a.getLtvPercent(), a.getPropertyAddress(), a.getPropertyCity(),
            a.getPropertyState(), a.getPropertyZip(), a.getPropertyType());
    }

    private static BorrowerV2Dto toBorrowerDto(Borrower b) {
        return new BorrowerV2Dto(b.getId(), b.getExternalId(), b.getFirstName(), b.getMiddleInitial(),
            b.getLastName(), b.getDateOfBirth(), b.getEmail(), b.getPhone(), b.getAddressLine1(),
            b.getAddressLine2(), b.getCity(), b.getState(), b.getZipCode(), b.getCreditScore(),
            b.getEmploymentStatus(), b.getAnnualIncome(), b.getStatus());
    }

    private static PaymentV2Dto toPaymentDto(Payment p) {
        return new PaymentV2Dto(p.getId(), p.getLegacyId(), p.getLoanAccount().getAccountNumber(),
            p.getPaymentDate(), p.getTotalAmount(), p.getPrincipalAmount(), p.getInterestAmount(),
            p.getEscrowAmount(), p.getLateFee(), p.getType(), p.getStatus(), p.getReceivedDate(),
            p.getProcessedDate());
    }
}
