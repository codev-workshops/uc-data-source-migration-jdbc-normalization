package com.workshop.loanservice.service.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CodeExpansionTransformerTest {

  @ParameterizedTest
  @EnumSource(LegacyCodeSet.class)
  void everyKnownCodeExpandsWithoutViolation(LegacyCodeSet set) {
    CodeExpansionTransformer t = new CodeExpansionTransformer(set);
    for (Map.Entry<String, String> e : set.expansions().entrySet()) {
      TransformResult<String> r = t.transform(e.getKey(), set.name());
      assertThat(r.isValid()).isTrue();
      assertThat(r.value()).isEqualTo(e.getValue());
    }
  }

  @Test
  void dictionariesAreExhaustive() {
    assertThat(LegacyCodeSet.BORR_STAT_CD.expansions()).containsOnlyKeys("ACT", "INA");
    assertThat(LegacyCodeSet.LN_STAT_CD.expansions()).containsOnlyKeys("ACT", "CLO", "DFT", "FRB");
    assertThat(LegacyCodeSet.PMT_TYP_CD.expansions()).containsOnlyKeys("REG", "EXT", "PRT", "PRE");
    assertThat(LegacyCodeSet.PMT_STAT_CD.expansions()).containsOnlyKeys("PST", "REV", "NSF", "PND");
    assertThat(LegacyCodeSet.PROP_TYP_CD.expansions()).containsOnlyKeys("SFR", "CND", "MFR", "TWN");
  }

  @Test
  void unmappedCodeBecomesUnknownWithErrorNotPassthrough() {
    CodeExpansionTransformer t = new CodeExpansionTransformer(LegacyCodeSet.PROP_TYP_CD);
    TransformResult<String> r = t.transform("MOB", "PROP_TYP_CD");
    assertThat(r.value()).isEqualTo(CodeExpansionTransformer.UNKNOWN);
    assertThat(r.value()).isNotEqualTo("MOB");
    assertThat(r.hasError()).isTrue();
    assertThat(r.violations().get(0).ruleId()).isEqualTo(CodeExpansionTransformer.RULE_UNMAPPED);
    assertThat(r.violations().get(0).rawValue()).isEqualTo("MOB");
  }

  @Test
  void codeLookupIsCaseSensitiveAndTrimmed() {
    CodeExpansionTransformer t = new CodeExpansionTransformer(LegacyCodeSet.LN_STAT_CD);
    assertThat(t.transform(" ACT ", "LN_STAT_CD").value()).isEqualTo("Active");
    assertThat(t.transform("act", "LN_STAT_CD").violations().get(0).ruleId())
        .isEqualTo(CodeExpansionTransformer.RULE_UNMAPPED);
  }

  @Test
  void missingCodeBecomesUnknownWithViolation() {
    CodeExpansionTransformer t = new CodeExpansionTransformer(LegacyCodeSet.PMT_STAT_CD);
    for (String raw : new String[] {null, "", "  "}) {
      TransformResult<String> r = t.transform(raw, "PMT_STAT_CD");
      assertThat(r.value()).isEqualTo(CodeExpansionTransformer.UNKNOWN);
      assertThat(r.violations().get(0).ruleId()).isEqualTo(CodeExpansionTransformer.RULE_MISSING);
    }
  }
}
