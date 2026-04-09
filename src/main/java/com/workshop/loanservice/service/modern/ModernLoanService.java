package com.workshop.loanservice.service.modern;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Modern service layer that reads from DynamoDB tables.
 * Replaces the legacy {@link com.workshop.loanservice.service.LoanService}.
 *
 * <p>Key improvements over legacy service:
 * <ul>
 *   <li>No string-to-type parsing required (DynamoDB stores proper types)</li>
 *   <li>No status code expansion needed (stored as full values)</li>
 *   <li>Normalized data model (borrower info fetched separately, not embedded in loans)</li>
 *   <li>Proper date ordering via ISO 8601 composite sort keys</li>
 * </ul>
 *
 * <p>The API contract (endpoints and response shapes) remains identical to the legacy service.
 */
@Service
public class ModernLoanService {

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final LoanProductRepository loanProductRepository;
    private final PaymentRepository paymentRepository;

    public ModernLoanService(BorrowerRepository borrowerRepository,
                             LoanAccountRepository loanAccountRepository,
                             LoanProductRepository loanProductRepository,
                             PaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LoanProduct::getProductCode, p -> p));

        Map<String, Borrower> borrowers = borrowerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Borrower::getBorrowerId, b -> b));

        return loanAccountRepository.findAll().stream()
                .map(acct -> toLoanSummary(acct,
                        products.get(acct.getProductCode()),
                        borrowers.get(acct.getBorrowerId())))
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String accountNumber) {
        LoanAccount acct = loanAccountRepository.findById(accountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + accountNumber));
        LoanProduct product = loanProductRepository.findById(acct.getProductCode())
                .orElse(null);
        Borrower borrower = borrowerRepository.findById(acct.getBorrowerId())
                .orElse(null);
        return toLoanSummary(acct, product, borrower);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        Map<String, LoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LoanProduct::getProductCode, p -> p));

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrowerId)
                .stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode()), borrower))
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String loanAccountId) {
        return paymentRepository.findByLoanAccountIdOrderByDateDesc(loanAccountId)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING METHODS
    // Much simpler than legacy — no string parsing or code expansion needed.
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct, LoanProduct product, Borrower borrower) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());

        if (borrower != null) {
            dto.setBorrowerName(borrower.getFirstName() + " " + borrower.getLastName());
        } else {
            dto.setBorrowerName("Unknown");
        }

        dto.setProductDescription(product != null ? product.getName() : acct.getProductCode());
        dto.setOriginalAmount(acct.getOriginalAmount());
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(acct.getStatus());
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(acct.getPropertyType());
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        String middle = borrower.getMiddleInitial() != null
                ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhone());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getState());
        dto.setCreditScore(borrower.getCreditScore());
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }

    private PaymentDto toPaymentDto(Payment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getPaymentId());
        dto.setLoanAccountNumber(pmt.getLoanAccountId());
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(pmt.getType());
        dto.setStatus(pmt.getStatus());
        return dto;
    }
}
