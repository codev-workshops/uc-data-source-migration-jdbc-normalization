package com.workshop.paymentservice.service;

import com.workshop.common.dto.PaymentDto;
import com.workshop.common.util.LegacyDataParser;
import com.workshop.paymentservice.entity.LegacyPayment;
import com.workshop.paymentservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final LegacyPaymentRepository paymentRepository;

    public PaymentService(LegacyPaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public List<PaymentDto> getPaymentsByLoanAccountNumber(String loanAccountNumber) {
        return paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private PaymentDto toPaymentDto(LegacyPayment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getPaymentSequenceNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccountNumber());
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(LegacyDataParser.parseLegacyAmount(pmt.getTotalAmount()));
        dto.setPrincipalAmount(LegacyDataParser.parseLegacyAmount(pmt.getPrincipalAmount()));
        dto.setInterestAmount(LegacyDataParser.parseLegacyAmount(pmt.getInterestAmount()));
        dto.setEscrowAmount(LegacyDataParser.parseLegacyAmount(pmt.getEscrowAmount()));
        dto.setLateFee(LegacyDataParser.parseLegacyAmount(pmt.getLateFee()));
        dto.setType(LegacyDataParser.expandPaymentType(pmt.getTypeCode()));
        dto.setStatus(LegacyDataParser.expandPaymentStatus(pmt.getStatusCode()));
        return dto;
    }
}
