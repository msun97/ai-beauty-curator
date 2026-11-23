-- 추천·저장·비교 (docs/ERD.md 6장)

CREATE TABLE rule_versions (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version     varchar(20) NOT NULL,
    weights     jsonb       NOT NULL,
    thresholds  jsonb       NOT NULL,
    change_note text,
    is_active   boolean     NOT NULL DEFAULT false,
    created_by  bigint      REFERENCES users (id) ON DELETE SET NULL,
    created_at  timestamptz NOT NULL,
    CONSTRAINT uk_rule_versions_version UNIQUE (version)
);
-- 동시에 적용되는 규칙은 하나만
CREATE UNIQUE INDEX uk_rule_versions_active ON rule_versions (is_active) WHERE is_active;

CREATE TABLE recommendations (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rule_version_id  bigint      NOT NULL REFERENCES rule_versions (id),
    category_id      bigint      REFERENCES categories (id), -- NULL이면 전체
    profile_snapshot jsonb       NOT NULL,                   -- 계산에 쓴 프로필 (FR-REC-07)
    excluded_count   integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL,
    CONSTRAINT ck_recommendations_excluded CHECK (excluded_count >= 0)
);
CREATE INDEX ix_recommendations_user_created ON recommendations (user_id, created_at DESC);

CREATE TABLE recommendation_items (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recommendation_id bigint       NOT NULL REFERENCES recommendations (id) ON DELETE CASCADE,
    product_id        bigint       NOT NULL REFERENCES products (id),
    rank              smallint     NOT NULL,
    total_score       numeric(5, 2) NOT NULL,
    badge             varchar(10)  NOT NULL,
    score_breakdown   jsonb        NOT NULL,
    CONSTRAINT uk_recommendation_items_rank UNIQUE (recommendation_id, rank),
    CONSTRAINT ck_recommendation_items_badge CHECK (badge IN ('GOOD', 'CHECK', 'AVOID', 'NO_DATA'))
);

CREATE TABLE recommendation_feedbacks (
    user_id                bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id             bigint      NOT NULL REFERENCES products (id),
    feedback               varchar(20) NOT NULL,
    recommendation_item_id bigint      REFERENCES recommendation_items (id) ON DELETE SET NULL,
    created_at             timestamptz NOT NULL,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT ck_recommendation_feedbacks_feedback CHECK (feedback IN ('INTERESTED', 'NOT_INTERESTED'))
);

CREATE TABLE bookmarks (
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id bigint      NOT NULL REFERENCES products (id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, product_id)
);

-- 최대 3개 제한은 애플리케이션에서 검증한다 (FR-CMP-01)
CREATE TABLE compare_items (
    user_id    bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id bigint      NOT NULL REFERENCES products (id),
    added_at   timestamptz NOT NULL,
    PRIMARY KEY (user_id, product_id)
);
