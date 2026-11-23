-- 트렌드 (docs/ERD.md 7장), 운영 (8장)

-- ---------------------------------------------------------------------------
-- 외부 관심도 수집 (FR-TRD-06, FR-ADM-09)
-- ---------------------------------------------------------------------------

CREATE TABLE trend_keywords (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id       bigint       REFERENCES products (id),
    ingredient_id    bigint       REFERENCES ingredients (id),
    group_name       varchar(100) NOT NULL,
    keywords         text[]       NOT NULL, -- 동의어 포함
    exclude_keywords text[]       NOT NULL DEFAULT '{}',
    is_anchor        boolean      NOT NULL DEFAULT false, -- 요청 간 상대값 보정용 공통 기준 키워드
    is_active        boolean      NOT NULL DEFAULT true,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT uk_trend_keywords_group_name UNIQUE (group_name),
    CONSTRAINT ck_trend_keywords_not_empty CHECK (cardinality(keywords) > 0),
    -- 기준 키워드는 특정 대상에 속하지 않고, 나머지는 상품과 성분 중 정확히 하나에 속한다
    CONSTRAINT ck_trend_keywords_target CHECK (
        (is_anchor AND product_id IS NULL AND ingredient_id IS NULL)
        OR (NOT is_anchor AND num_nonnulls(product_id, ingredient_id) = 1)
    )
);
CREATE INDEX ix_trend_keywords_product ON trend_keywords (product_id);
CREATE INDEX ix_trend_keywords_ingredient ON trend_keywords (ingredient_id);

CREATE TABLE trend_collection_batches (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    period_start  date        NOT NULL,
    period_end    date        NOT NULL,
    request_count integer     NOT NULL DEFAULT 0,
    status        varchar(10) NOT NULL,
    error_message text,
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    CONSTRAINT ck_trend_collection_batches_status CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_trend_collection_batches_period CHECK (period_start <= period_end)
);

-- API가 준 상대값을 원본 그대로 저장하고, 보정값은 따로 계산해 둔다
CREATE TABLE search_trend_data (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trend_keyword_id bigint      NOT NULL REFERENCES trend_keywords (id) ON DELETE CASCADE,
    batch_id         bigint      NOT NULL REFERENCES trend_collection_batches (id),
    week_start       date        NOT NULL,
    raw_ratio        numeric(9, 5) NOT NULL,
    anchor_ratio     numeric(9, 5),
    normalized_value numeric(12, 5),
    CONSTRAINT uk_search_trend_data UNIQUE (trend_keyword_id, week_start, batch_id)
);
CREATE INDEX ix_search_trend_data_batch ON search_trend_data (batch_id);

-- ---------------------------------------------------------------------------
-- 서비스 내 행동 (NFR-PRIV-05)
-- actor_key에는 FK를 두지 않는다. 탈퇴로 계정이 지워지면 누구의 행동인지 알 수 없게 된다.
-- ---------------------------------------------------------------------------

CREATE TABLE product_actions (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_key     uuid        NOT NULL,
    product_id    bigint      REFERENCES products (id),
    ingredient_id bigint      REFERENCES ingredients (id),
    action_type   varchar(10) NOT NULL,
    occurred_at   timestamptz NOT NULL,
    CONSTRAINT ck_product_actions_type CHECK (action_type IN ('VIEW', 'BOOKMARK', 'COMPARE', 'SHARE')),
    CONSTRAINT ck_product_actions_target CHECK (num_nonnulls(product_id, ingredient_id) = 1)
);
CREATE INDEX ix_product_actions_product_occurred ON product_actions (product_id, occurred_at) WHERE product_id IS NOT NULL;
CREATE INDEX ix_product_actions_ingredient_occurred ON product_actions (ingredient_id, occurred_at) WHERE ingredient_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 주간 트렌드 스냅샷 (FR-TRD-02~04)
-- ---------------------------------------------------------------------------

CREATE TABLE trend_snapshots (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_type    varchar(10) NOT NULL,
    period_start   date        NOT NULL,
    period_end     date        NOT NULL,
    method_version varchar(20) NOT NULL,
    weights        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL,
    CONSTRAINT uk_trend_snapshots_period UNIQUE (target_type, period_start),
    CONSTRAINT ck_trend_snapshots_target CHECK (target_type IN ('PRODUCT', 'INGREDIENT')),
    CONSTRAINT ck_trend_snapshots_period CHECK (period_start <= period_end)
);

CREATE TABLE trend_snapshot_items (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    snapshot_id         bigint        NOT NULL REFERENCES trend_snapshots (id) ON DELETE CASCADE,
    product_id          bigint        REFERENCES products (id),
    ingredient_id       bigint        REFERENCES ingredients (id),
    rank                smallint      NOT NULL,
    total_score         numeric(7, 3) NOT NULL,
    search_growth_rate  numeric(9, 3), -- 근거 표시용 (예: 검색량 32% 증가)
    service_growth_rate numeric(9, 3),
    previous_rank       smallint,
    rank_change         varchar(10)   NOT NULL,
    CONSTRAINT uk_trend_snapshot_items_rank UNIQUE (snapshot_id, rank),
    CONSTRAINT ck_trend_snapshot_items_target CHECK (num_nonnulls(product_id, ingredient_id) = 1),
    CONSTRAINT ck_trend_snapshot_items_change CHECK (rank_change IN ('UP', 'DOWN', 'NEW', 'SAME'))
);

-- 운영자 선정. 집계 순위와 섞이지 않게 스냅샷과 분리한다 (FR-ADM-06)
CREATE TABLE trend_curations (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint       NOT NULL REFERENCES products (id),
    reason     varchar(200) NOT NULL,
    source_url varchar(500),
    start_at   timestamptz  NOT NULL,
    end_at     timestamptz  NOT NULL,
    created_by bigint       REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_trend_curations_period CHECK (start_at < end_at)
);
CREATE INDEX ix_trend_curations_period ON trend_curations (start_at, end_at);

-- ---------------------------------------------------------------------------
-- 운영: AI 사용량 (NFR-AI-04, FR-ADM-07)
-- ---------------------------------------------------------------------------

CREATE TABLE ai_usage_logs (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_type      varchar(20)    NOT NULL,
    model_name    varchar(100)   NOT NULL,
    input_tokens  integer        NOT NULL DEFAULT 0,
    output_tokens integer        NOT NULL DEFAULT 0,
    cost_usd      numeric(10, 6) NOT NULL DEFAULT 0,
    status        varchar(10)    NOT NULL,
    created_at    timestamptz    NOT NULL,
    CONSTRAINT ck_ai_usage_logs_job_type CHECK (job_type IN ('REVIEW_ANALYSIS', 'SUMMARY')),
    CONSTRAINT ck_ai_usage_logs_status CHECK (status IN ('SUCCESS', 'FAILED'))
);
-- 월별 비용 합계 조회
CREATE INDEX ix_ai_usage_logs_created ON ai_usage_logs (created_at);
