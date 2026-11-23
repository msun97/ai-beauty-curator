-- 리뷰 원문과 AI 분석 결과 (docs/ERD.md 5장)
-- 원문(reviews)과 분석 결과를 분리해 프롬프트가 바뀌어도 원문에서 다시 분석할 수 있게 한다.

CREATE TABLE reviews (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id         bigint       NOT NULL REFERENCES products (id),
    data_source_id     bigint       NOT NULL REFERENCES data_sources (id),
    external_id        varchar(100) NOT NULL, -- 출처 안에서의 식별자 (중복 등록 방지)
    content            text         NOT NULL,
    rating             smallint,
    reviewer_skin_type varchar(20),
    written_at         date,
    analysis_status    varchar(20)  NOT NULL DEFAULT 'PENDING',
    created_at         timestamptz  NOT NULL,
    CONSTRAINT uk_reviews_source_external UNIQUE (data_source_id, external_id),
    CONSTRAINT ck_reviews_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_skin_type CHECK (reviewer_skin_type IN ('DRY', 'OILY', 'COMBINATION', 'NORMAL')),
    CONSTRAINT ck_reviews_analysis_status CHECK (analysis_status IN ('PENDING', 'DONE', 'FAILED', 'EXCLUDED'))
);
CREATE INDEX ix_reviews_product_written ON reviews (product_id, written_at DESC);
-- 분석 배치가 대기 중인 리뷰만 빠르게 찾도록
CREATE INDEX ix_reviews_pending ON reviews (id) WHERE analysis_status = 'PENDING';

CREATE TABLE review_analyses (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id      bigint       NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    prompt_version varchar(20)  NOT NULL,
    model_name     varchar(100) NOT NULL,
    sentiment      varchar(10),
    status         varchar(10)  NOT NULL,
    error_message  varchar(500),
    analyzed_at    timestamptz  NOT NULL,
    CONSTRAINT uk_review_analyses_review_prompt UNIQUE (review_id, prompt_version),
    CONSTRAINT ck_review_analyses_sentiment CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE')),
    CONSTRAINT ck_review_analyses_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE TABLE review_analysis_topics (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_id       bigint      NOT NULL REFERENCES review_analyses (id) ON DELETE CASCADE,
    topic             varchar(20) NOT NULL,
    sentiment         varchar(10) NOT NULL,
    evidence_sentence text,
    CONSTRAINT ck_review_analysis_topics_topic CHECK (topic IN ('MOISTURE', 'TEXTURE', 'SCENT', 'FEEL', 'PRICE', 'LASTING')),
    CONSTRAINT ck_review_analysis_topics_sentiment CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE'))
);
CREATE INDEX ix_review_analysis_topics_analysis ON review_analysis_topics (analysis_id);

CREATE TABLE review_analysis_concerns (
    analysis_id bigint      NOT NULL REFERENCES review_analyses (id) ON DELETE CASCADE,
    concern_id  bigint      NOT NULL REFERENCES skin_concerns (id),
    sentiment   varchar(10) NOT NULL,
    PRIMARY KEY (analysis_id, concern_id),
    CONSTRAINT ck_review_analysis_concerns_sentiment CHECK (sentiment IN ('POSITIVE', 'NEUTRAL', 'NEGATIVE'))
);

CREATE TABLE product_review_summaries (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id            bigint       NOT NULL REFERENCES products (id),
    prompt_version        varchar(20)  NOT NULL,
    model_name            varchar(100) NOT NULL,
    analyzed_review_count integer      NOT NULL,
    review_period_start   date,
    review_period_end     date,
    status                varchar(10)  NOT NULL,
    is_current            boolean      NOT NULL DEFAULT false,
    analyzed_at           timestamptz  NOT NULL,
    CONSTRAINT ck_product_review_summaries_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_product_review_summaries_count CHECK (analyzed_review_count >= 0)
);
CREATE INDEX ix_product_review_summaries_product ON product_review_summaries (product_id);
-- 상품마다 화면에 보여줄 현재 요약은 하나만
CREATE UNIQUE INDEX uk_product_review_summaries_current ON product_review_summaries (product_id) WHERE is_current;

CREATE TABLE review_summary_lines (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary_id bigint   NOT NULL REFERENCES product_review_summaries (id) ON DELETE CASCADE,
    line_no    smallint NOT NULL,
    content    text     NOT NULL,
    CONSTRAINT uk_review_summary_lines_no UNIQUE (summary_id, line_no),
    CONSTRAINT ck_review_summary_lines_no CHECK (line_no BETWEEN 1 AND 3)
);

-- 요약 문장의 근거가 된 원문 문장 (FR-REV-03 근거 보기)
CREATE TABLE review_summary_evidences (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    summary_line_id bigint NOT NULL REFERENCES review_summary_lines (id) ON DELETE CASCADE,
    review_id       bigint NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    sentence        text   NOT NULL
);
CREATE INDEX ix_review_summary_evidences_line ON review_summary_evidences (summary_line_id);
CREATE INDEX ix_review_summary_evidences_review ON review_summary_evidences (review_id);

-- 비교 화면용 주제별 집계 캐시 (분석 배치 후 갱신)
CREATE TABLE product_review_stats (
    product_id     bigint      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    topic          varchar(20) NOT NULL,
    positive_count integer     NOT NULL DEFAULT 0,
    neutral_count  integer     NOT NULL DEFAULT 0,
    negative_count integer     NOT NULL DEFAULT 0,
    updated_at     timestamptz NOT NULL,
    PRIMARY KEY (product_id, topic),
    CONSTRAINT ck_product_review_stats_topic CHECK (topic IN ('MOISTURE', 'TEXTURE', 'SCENT', 'FEEL', 'PRICE', 'LASTING'))
);
