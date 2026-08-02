# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that currently connects to a **legacy data warehouse** (simulated via H2 with legacy-style schemas). The workshop challenge is to migrate the data source to a **modern schema** while keeping the application functional.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It currently reads from legacy tables with denormalized structures, cryptic column names, and outdated patterns. The goal is to rewire it to use a normalized modern schema with clear naming conventions.

## Architecture

Detailed narrative, measurements and decisions: [`docs/ARCHITECTURE_ANALYSIS.md`](docs/ARCHITECTURE_ANALYSIS.md).
Diagrams below show the **as-is (legacy)** and **to-be (modern)** states.

### 1. Component diagram

```mermaid
flowchart TB
    client([HTTP client])

    subgraph app["loan-service (Spring Boot 3.2.3, Tomcat)"]
        subgraph web["Web layer"]
            lc["LoanController<br/>/api/loans"]
            bc["BorrowerController<br/>/api/borrowers"]
            lc2["LoanControllerV2<br/>/api/v2/loans (new)"]
            bc2["BorrowerControllerV2<br/>/api/v2/borrowers (new)"]
            adv["RestControllerAdvice<br/>404 / 400, no stack traces (new)"]
        end
        svc["LoanService<br/>DTO assembly"]
        prov{{"LoanDataProvider<br/>legacy | modern | dual-read<br/>(feature flag, new)"}}
        legacyRepo["Legacy repositories<br/>(deprecated, migration input)"]
        modernRepo["Modern repositories<br/>(new)"]
        mig["Migration job<br/>extract → transform → load → validate (new)"]
        recon["Reconciler<br/>drift detection (new)"]
        pay["PaymentPostingService<br/>internal write path (new)"]
        cache[("Caffeine cache<br/>products, borrower display (new)")]
        obs["Actuator + Micrometer<br/>metrics, tracing (new)"]
    end

    legacyDb[("H2 · legacy CDW schema<br/>CDW_* tables")]
    modernDb[("H2 · modern schema<br/>borrowers, loan_products,<br/>loan_accounts, payments")]
    h2c["/h2-console<br/>(dev profile only)"]
    prom["Prometheus scrape<br/>/actuator/prometheus"]

    client --> lc & bc & lc2 & bc2
    lc & bc & lc2 & bc2 --> svc --> prov
    adv -.-> client
    prov --> legacyRepo & modernRepo
    prov -.-> cache
    legacyRepo --> legacyDb
    modernRepo --> modernDb
    mig --> legacyRepo & modernRepo
    recon --> legacyDb & modernDb
    pay --> modernRepo
    pay -. dual-write .-> legacyRepo
    obs --> prom
    h2c -.-> legacyDb & modernDb

    classDef new fill:#e8f5e9,stroke:#2e7d32
    class lc2,bc2,adv,prov,modernRepo,mig,recon,pay,cache,obs,modernDb new
```

Green = added by the migration. Everything else exists today.

### 2. Dependency graph

Bean-level wiring (as-is solid, to-be dashed):

```mermaid
flowchart LR
    LC[LoanController] --> LS[LoanService]
    BC[BorrowerController] --> LS
    LS --> LBR[LegacyBorrowerRepository]
    LS --> LLAR[LegacyLoanAccountRepository]
    LS --> LLPR[LegacyLoanProductRepository]
    LS --> LPR[LegacyPaymentRepository]
    LBR & LLAR & LLPR & LPR --> DS[(legacy DataSource)]

    LS -.-> P{{LoanDataProvider}}
    P -.-> LEG[LegacyLoanDataProvider]
    P -.-> MOD[ModernLoanDataProvider]
    P -.-> DUAL[DualReadLoanDataProvider]
    LEG -.-> LBR
    MOD -.-> BR[BorrowerRepository] & LAR[LoanAccountRepository] & LPR2[LoanProductRepository] & PR[PaymentRepository]
    BR & LAR & LPR2 & PR -.-> MDS[(modern DataSource)]
    MIG[LegacyToModernMigrationService] -.-> LBR & BR
```

No cycles; no controller reaches a repository directly. Key runtime dependencies from
`mvn dependency:tree`: Spring Boot 3.2.3 · Spring 6.1.4 · Hibernate ORM 6.4.4 · Spring Data JPA 3.2.3 ·
HikariCP 5.0.1 · Jackson 2.15.4 · Tomcat 10.1.19 · H2 2.2.224 · Logback 1.4.14. Micrometer
`observation` 1.12.3 is present transitively, but **no actuator and no metrics registry** are wired.

### 3. Package structure

```mermaid
flowchart TB
    subgraph now["Current"]
        n1[controller] --> n2[service] --> n3[repository] --> n4[entity]
        n2 --> n5[dto]
    end
    subgraph target["Target"]
        t1[controller<br/>v1 + v2] --> t2[service]
        t2 --> t3{{provider}}
        t3 --> t4[modern.repository] --> t5[modern.entity]
        t3 --> t6["legacy.repository<br/>(deprecated)"] --> t7["legacy.entity<br/>(deprecated)"]
        t8[migration] --> t4
        t8 --> t6
        t9[config] -.-> t3
        t10[observability] -.-> t2
        t11[security] -.-> t1
        t2 --> t12[dto]
    end
```

Rule: `controller → service → provider → repository`. Only `migration` may touch `legacy.*`.

### 4a. Data flow — request path

```mermaid
flowchart LR
    req["GET /api/loans/{id}"] --> ctl[LoanController]
    ctl --> val["@Pattern validation<br/>(new)"]
    val --> svc[LoanService]
    svc --> prov{{LoanDataProvider}}
    prov -->|legacy| lsql["SELECT ... FROM CDW_LN_ACCT<br/>all VARCHAR"]
    lsql --> parse["parseLegacyAmount / expandStatusCode<br/>string → BigDecimal, code → label"]
    prov -->|modern| msql["SELECT ... FROM loan_accounts<br/>JOIN borrowers JOIN loan_products<br/>already typed"]
    msql --> fmt["format LocalDate → MM/DD/YYYY<br/>code → display label"]
    parse & fmt --> dto[LoanSummaryDto]
    dto --> json["identical JSON"]
```

### 4b. Data flow — migration

```mermaid
flowchart LR
    A[(CDW_BORR_MSTR<br/>CDW_LN_PROD<br/>CDW_LN_ACCT<br/>CDW_PMT_HIST)] --> B[Extract<br/>chunked reads]
    B --> C[Transform<br/>LegacyValueParser · CodeTranslator]
    C --> D[Resolve FKs<br/>external_id → id<br/>code → id<br/>account_number → id]
    D --> E[Load<br/>batched, idempotent upsert]
    E --> F[(borrowers<br/>loan_products<br/>loan_accounts<br/>payments)]
    C -->|malformed| R[Rejects<br/>reason-coded, never dropped]
    E --> V[Validate<br/>row counts · sums · orphan FKs]
    V --> G[MigrationReport + metrics]
    R --> G
```

### 5a. ER diagram — legacy (as-is)

```mermaid
erDiagram
    CDW_BORR_MSTR {
        VARCHAR BORR_ID PK
        VARCHAR BORR_FST_NM
        VARCHAR BORR_LST_NM
        VARCHAR BORR_DOB_DT "MM/DD/YYYY string"
        VARCHAR BORR_CRDT_SCR "number as string"
        VARCHAR BORR_ANN_INCM "'92,500'"
        VARCHAR BORR_STAT_CD "ACT / INA"
        VARCHAR BORR_REC_TYP "dropped"
    }
    CDW_LN_PROD {
        VARCHAR PROD_CD PK
        VARCHAR PROD_TERM_MOS "string"
        VARCHAR PROD_STAT_CD "ACT / INA"
    }
    CDW_LN_ACCT {
        VARCHAR LN_ACCT_NBR PK
        VARCHAR BORR_ID "no FK constraint"
        VARCHAR BORR_FST_NM "DENORMALIZED"
        VARCHAR BORR_LST_NM "DENORMALIZED"
        VARCHAR BORR_SSN_LST4 "DENORMALIZED"
        VARCHAR PROD_CD "no FK constraint"
        VARCHAR LN_CURR_BAL "'271,432.56'"
        VARCHAR LN_STAT_CD "ACT/CLO/DFT/FRB"
    }
    CDW_PMT_HIST {
        VARCHAR PMT_SEQ_NBR PK
        VARCHAR LN_ACCT_NBR "no FK constraint"
        VARCHAR PMT_AMT "string"
        VARCHAR PMT_TYP_CD "REG/EXT/PRT/PRE"
    }
    CDW_BORR_MSTR ||..o{ CDW_LN_ACCT : "BORR_ID (soft link)"
    CDW_LN_PROD ||..o{ CDW_LN_ACCT : "PROD_CD (soft link)"
    CDW_LN_ACCT ||..o{ CDW_PMT_HIST : "LN_ACCT_NBR (soft link)"
```

### 5b. ER diagram — modern (to-be)

```mermaid
erDiagram
    borrowers {
        BIGINT id PK
        VARCHAR external_id UK "legacy BORR_ID"
        VARCHAR first_name
        VARCHAR last_name
        DATE date_of_birth
        INTEGER credit_score
        DECIMAL annual_income
        VARCHAR status "ACTIVE / INACTIVE"
        TIMESTAMP created_at
    }
    loan_products {
        BIGINT id PK
        VARCHAR code UK
        INTEGER term_months
        BOOLEAN is_active
        DATE effective_date
    }
    loan_accounts {
        BIGINT id PK
        VARCHAR account_number UK
        BIGINT borrower_id FK
        BIGINT product_id FK
        DECIMAL current_balance
        DECIMAL interest_rate
        DATE origination_date
        VARCHAR status "ACTIVE/CLOSED/DEFAULT/FORBEARANCE"
        INTEGER version "optimistic lock (new)"
    }
    payments {
        BIGINT id PK
        BIGINT loan_account_id FK
        DATE payment_date
        DECIMAL total_amount
        VARCHAR type "REGULAR/EXTRA/PARTIAL/PREPAYMENT"
        VARCHAR status "POSTED/REVERSED/NSF/PENDING"
    }
    borrowers ||--o{ loan_accounts : has
    loan_products ||--o{ loan_accounts : classifies
    loan_accounts ||--o{ payments : receives
```

Index plan (see `docs/ARCHITECTURE_ANALYSIS.md` §3.3): drop `idx_payments_loan` (redundant with the
FK constraint index), `idx_payments_date`, `idx_borrowers_email` and `idx_borrowers_status` — all
unused by any query and pure write-side cost. The two composite indexes that look obvious,
`payments(loan_account_id, payment_date DESC)` and `loan_accounts(status, id)`, were **measured and
rejected on H2** (the latter made the status query 31% slower); they remain recommendations for
PostgreSQL only.

### 6a. Sequence — migration run

```mermaid
sequenceDiagram
    participant R as ApplicationRunner<br/>(profile=migration)
    participant M as MigrationService
    participant L as Legacy DB
    participant D as Modern DB
    participant V as Validator
    R->>M: run()
    loop per table, chunked (10k rows)
        M->>L: SELECT chunk
        L-->>M: legacy rows (all VARCHAR)
        M->>M: parse dates/amounts, expand codes
        M->>D: resolve FK (external_id → id)
        Note over M,D: REQUIRES_NEW per chunk
        M->>D: batch upsert
        D-->>M: ack
        M->>M: report.add(read, written, rejected)
    end
    M->>V: validate()
    V->>D: counts, sums, orphan FK checks
    V-->>M: pass / fail
    M-->>R: MigrationReport + metrics
```

### 6b. Sequence — concurrent payment posting (transactions & locks)

```mermaid
sequenceDiagram
    participant T1 as Thread 1
    participant T2 as Thread 2
    participant DB as loan_accounts / payments
    Note over T1,T2: lock order is always parent → child
    T1->>DB: BEGIN (READ_COMMITTED)
    T1->>DB: INSERT payment (append-only, no contention)
    T1->>DB: UPDATE loan_accounts SET balance=balance-? WHERE id=? AND version=?
    activate DB
    T2->>DB: BEGIN
    T2->>DB: INSERT payment
    T2->>DB: UPDATE same row → waits on row lock
    T1->>DB: COMMIT
    deactivate DB
    Note right of T2: version changed ⇒ OptimisticLockException<br/>bounded retry with backoff + idempotency key
    T2->>DB: retry with fresh version → COMMIT
```

Measured (H2, 16 threads): same-row updates cost ~2.2× throughput and **60× worse tail latency**
(0.4 ms → 24.7 ms) versus distinct rows — the reason balances are updated atomically/versioned rather
than read-modify-write. Full analysis in `docs/ARCHITECTURE_ANALYSIS.md` §3.8.

## Current State (Legacy)

The app connects to legacy tables:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Target State (Modern)

Migrate to normalized tables:
- `borrowers` — Clean borrower records
- `loan_products` — Product catalog
- `loan_accounts` — Normalized loan accounts with foreign keys
- `payments` — Payment records

See `data/modern-schema/` for target DDL.

## Quick Start

```bash
mvn spring-boot:run
```

(The Maven wrapper is not committed — `.mvn/wrapper/` exists but `mvnw` does not, so use `mvn`
directly until the wrapper is added.)

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/loans/{loanId}/payments` — Payment history for a loan
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans

> These are the paths the controllers actually expose. Earlier revisions of this README documented
> `GET /api/payments/loan/{loanId}`, which has never existed in the code.

## Scale, concurrency and performance

The five endpoints above are unbounded by design and stay that way (v1 contract is frozen). At the
target volume of ~500k loan accounts the unbounded list endpoints are unsafe; paginated `/api/v2`
endpoints are being added alongside them. Measured H2 evidence, index recommendations, caching and
concurrency analysis: [`docs/ARCHITECTURE_ANALYSIS.md`](docs/ARCHITECTURE_ANALYSIS.md), raw benchmark
output in [`docs/perf/`](docs/perf/), harness in [`perf/ScaleBench.java`](perf/ScaleBench.java)
(run: `java -Xmx16g -cp <h2.jar> perf/ScaleBench.java`).

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
