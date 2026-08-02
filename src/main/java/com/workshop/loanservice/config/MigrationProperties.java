package com.workshop.loanservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("loanservice.migration")
public class MigrationProperties {

    /** Run the backfill during startup. Safe to leave on: the migration is idempotent. */
    private boolean runOnStartup = true;

    /** Rows per transaction. Bounds both transaction duration and persistence-context size. */
    private int chunkSize = 1000;

    /** Fail the whole run on the first rejected row ("strict") or report and continue ("lenient"). */
    private String mode = "strict";

    /** Hard ceiling on a single chunk transaction, so a stuck chunk cannot hold locks forever. */
    private int chunkTimeoutSeconds = 60;

    public boolean isStrict() {
        return "strict".equalsIgnoreCase(mode);
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getChunkTimeoutSeconds() {
        return chunkTimeoutSeconds;
    }

    public void setChunkTimeoutSeconds(int chunkTimeoutSeconds) {
        this.chunkTimeoutSeconds = chunkTimeoutSeconds;
    }
}
