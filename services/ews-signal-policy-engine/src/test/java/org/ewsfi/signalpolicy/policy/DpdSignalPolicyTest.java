package org.ewsfi.signalpolicy.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DpdSignalPolicyTest {

    @Test
    void classifiesBandCrossingsAndSuppressesUnchangedValues() {
        assertThat(DpdSignalPolicy.classify(null, 0)).isNull();
        assertThat(DpdSignalPolicy.classify(0, 12)).isEqualTo(DpdSignalPolicy.EMERGED);
        assertThat(DpdSignalPolicy.classify(12, 20)).isNull();
        assertThat(DpdSignalPolicy.classify(20, 20)).isNull();
        assertThat(DpdSignalPolicy.classify(20, 45)).isEqualTo(DpdSignalPolicy.WORSENING);
        assertThat(DpdSignalPolicy.classify(45, 0)).isNull();
        assertThat(DpdSignalPolicy.classify(0, 8)).isEqualTo(DpdSignalPolicy.EMERGED);
        assertThat(DpdSignalPolicy.classify(40, 45)).isNull();
        assertThat(DpdSignalPolicy.classify(89, 90)).isEqualTo(DpdSignalPolicy.WORSENING);
    }
}
