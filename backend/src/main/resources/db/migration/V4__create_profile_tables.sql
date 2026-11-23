-- 피부 프로필 (docs/ERD.md 3장)
-- 개인정보이므로 회원 탈퇴 시 함께 삭제한다 (FR-AUTH-04, NFR-PRIV-04).

CREATE TABLE user_skin_profiles (
    user_id      bigint PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    skin_type    varchar(20) NOT NULL,
    is_sensitive boolean     NOT NULL,
    budget_min   integer,
    budget_max   integer,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT ck_user_skin_profiles_type CHECK (skin_type IN ('DRY', 'OILY', 'COMBINATION', 'NORMAL')),
    CONSTRAINT ck_user_skin_profiles_budget CHECK (
        (budget_min IS NULL OR budget_min >= 0)
        AND (budget_max IS NULL OR budget_max >= 0)
        AND (budget_min IS NULL OR budget_max IS NULL OR budget_min <= budget_max)
    )
);

-- 최대 3개 제한은 애플리케이션에서 검증한다 (FR-PRF-01)
CREATE TABLE user_skin_concerns (
    user_id    bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    concern_id bigint NOT NULL REFERENCES skin_concerns (id),
    PRIMARY KEY (user_id, concern_id)
);

CREATE TABLE user_interest_categories (
    user_id     bigint NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id bigint NOT NULL REFERENCES categories (id),
    PRIMARY KEY (user_id, category_id)
);

-- PK가 (user_id, ingredient_id)라 한 성분을 선호와 기피에 동시에 등록할 수 없다 (FR-PRF-02, 03)
CREATE TABLE user_ingredient_preferences (
    user_id         bigint      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ingredient_id   bigint      NOT NULL REFERENCES ingredients (id),
    preference_type varchar(20) NOT NULL,
    created_at      timestamptz NOT NULL,
    PRIMARY KEY (user_id, ingredient_id),
    CONSTRAINT ck_user_ingredient_preferences_type CHECK (preference_type IN ('PREFERRED', 'AVOIDED'))
);
