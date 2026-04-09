package com.workshop.loanservice;

import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@SpringBootTest
class LoanServiceApplicationTests {

    @MockBean
    private DynamoDbClient dynamoDbClient;

    @MockBean
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @MockBean
    private BorrowerRepository borrowerRepository;

    @MockBean
    private LoanAccountRepository loanAccountRepository;

    @MockBean
    private LoanProductRepository loanProductRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @Test
    void contextLoads() {
    }
}
