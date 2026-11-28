package com.workshop.loanservice.golden;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Legacy read path: every code expansion, plus null ("Unknown") and unknown codes (returned as-is).
 */
@SpringBootTest(properties = {"loan.datasource.mode=legacy", "loan.migration.run-on-startup=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyCodeExpansionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("legacyJdbcTemplate")
    private JdbcTemplate legacy;

    @ParameterizedTest(name = "loan status {0} -> {1}, property {2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            "ACT,  Active,       SFR,  Single Family Residence",
            "CLO,  Closed,       CND,  Condominium",
            "DFT,  Default,      MFR,  Multi-Family Residence",
            "FRB,  Forbearance,  TWN,  Townhouse",
            "XYZ,  XYZ,          ABC,  ABC",
            "NULL, Unknown,      NULL, Unknown"
    })
    void expandsLoanCodes(String statusCode, String status, String propertyCode, String propertyType) throws Exception {
        legacy.update("UPDATE CDW_LN_ACCT SET LN_STAT_CD = ?, PROP_TYP_CD = ? WHERE LN_ACCT_NBR = 'LN-2019-00142'",
                statusCode, propertyCode);

        mockMvc.perform(get("/api/loans/LN-2019-00142"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.propertyType").value(propertyType));
    }

    @ParameterizedTest(name = "payment type {0} -> {1}, status {2} -> {3}")
    @CsvSource(nullValues = "NULL", value = {
            "REG,  Regular,     PST,  Posted",
            "EXT,  Extra,       REV,  Reversed",
            "PRT,  Partial,     NSF,  Non-Sufficient Funds",
            "PRE,  Prepayment,  PND,  Pending",
            "XYZ,  XYZ,         ABC,  ABC",
            "NULL, Unknown,     NULL, Unknown"
    })
    void expandsPaymentCodes(String typeCode, String type, String statusCode, String status) throws Exception {
        legacy.update("UPDATE CDW_PMT_HIST SET PMT_TYP_CD = ?, PMT_STAT_CD = ? WHERE LN_ACCT_NBR = 'LN-2019-00142'",
                typeCode, statusCode);

        mockMvc.perform(get("/api/loans/LN-2019-00142/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value(type))
                .andExpect(jsonPath("$[0].status").value(status));
    }
}
