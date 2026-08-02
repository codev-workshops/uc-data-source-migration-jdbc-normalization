package com.workshop.loanservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime switch for the cutover. Rolling back is a property change and a restart, not a deploy.
 */
@Component
@ConfigurationProperties("loanservice")
public class ReadSourceProperties {

    public enum ReadSource {
        /** Pre-migration behaviour: serve everything from the legacy CDW tables. */
        LEGACY,
        /** Post-cutover behaviour: serve everything from the modern schema. */
        MODERN,
        /**
         * Reconciliation window: serve modern, read legacy in the same request and record any
         * difference. The legacy result is never returned, so a shadow-read failure cannot break a
         * response.
         */
        DUAL_READ
    }

    private ReadSource readSource = ReadSource.MODERN;

    /** Mirror writes into the legacy store so a rollback to LEGACY does not lose data. */
    private boolean dualWrite = true;

    private final V1 v1 = new V1();

    public static class V1 {
        /**
         * Row count above which an unbounded v1 response is reported. Reporting only: the v1
         * contract is frozen, so nothing is ever truncated.
         */
        private int largeResponseWarnThreshold = 1000;

        public int getLargeResponseWarnThreshold() {
            return largeResponseWarnThreshold;
        }

        public void setLargeResponseWarnThreshold(int largeResponseWarnThreshold) {
            this.largeResponseWarnThreshold = largeResponseWarnThreshold;
        }
    }

    public ReadSource getReadSource() {
        return readSource;
    }

    public void setReadSource(ReadSource readSource) {
        this.readSource = readSource;
    }

    public boolean isDualWrite() {
        return dualWrite;
    }

    public void setDualWrite(boolean dualWrite) {
        this.dualWrite = dualWrite;
    }

    public V1 getV1() {
        return v1;
    }
}
