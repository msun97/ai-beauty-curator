-- 성분·상품 카탈로그와 기준 코드 (docs/ERD.md 3장 기준 코드, 4장)

-- 성분명·상품명 부분 검색(자동완성)용. PostgreSQL 13부터 DB 소유자가 설치할 수 있는 신뢰 확장이다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- 기준 코드
-- ---------------------------------------------------------------------------

CREATE TABLE skin_concerns (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          varchar(30) NOT NULL,
    name          varchar(30) NOT NULL,
    display_order integer     NOT NULL,
    CONSTRAINT uk_skin_concerns_code UNIQUE (code)
);

CREATE TABLE categories (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          varchar(30) NOT NULL,
    name          varchar(30) NOT NULL,
    display_order integer     NOT NULL,
    CONSTRAINT uk_categories_code UNIQUE (code)
);

-- 화면 시안(피부 프로필 입력, 카테고리 필터) 기준
INSERT INTO skin_concerns (code, name, display_order) VALUES
    ('DRYNESS',    '건조',   1),
    ('TROUBLE',    '트러블', 2),
    ('OILINESS',   '유분',   3),
    ('PORE',       '모공',   4),
    ('DEAD_SKIN',  '각질',   5),
    ('REDNESS',    '붉은기', 6),
    ('ELASTICITY', '탄력',   7),
    ('DULLNESS',   '칙칙함', 8);

INSERT INTO categories (code, name, display_order) VALUES
    ('TONER',     '토너',   1),
    ('SERUM',     '세럼',   2),
    ('CREAM',     '크림',   3),
    ('SUNCARE',   '선케어', 4),
    ('CLEANSING', '클렌징', 5),
    ('MASK',      '마스크', 6);

-- ---------------------------------------------------------------------------
-- 데이터 출처 (NFR-LEG-02)
-- ---------------------------------------------------------------------------

CREATE TABLE data_sources (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         varchar(100) NOT NULL,
    source_type  varchar(20)  NOT NULL,
    url          varchar(500),
    usage_terms  text,
    collected_at date,
    CONSTRAINT uk_data_sources_name UNIQUE (name),
    CONSTRAINT ck_data_sources_type CHECK (source_type IN ('PUBLIC_DATA', 'FILE', 'API', 'MANUAL'))
);

-- ---------------------------------------------------------------------------
-- 성분
-- ---------------------------------------------------------------------------

CREATE TABLE ingredients (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    standard_name varchar(200) NOT NULL,
    inci_name     varchar(300),
    description   text,
    source_name   varchar(100),
    source_url    varchar(500),
    verified_at   date,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uk_ingredients_standard_name UNIQUE (standard_name)
);
CREATE INDEX ix_ingredients_standard_name_trgm ON ingredients USING gin (standard_name gin_trgm_ops);

-- 이명·영문명·흔한 표기 차이. normalized_alias는 공백·기호를 지운 비교용 값이다.
CREATE TABLE ingredient_aliases (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ingredient_id    bigint       NOT NULL REFERENCES ingredients (id) ON DELETE CASCADE,
    alias            varchar(300) NOT NULL,
    normalized_alias varchar(300) NOT NULL,
    CONSTRAINT uk_ingredient_aliases_normalized UNIQUE (normalized_alias)
);
CREATE INDEX ix_ingredient_aliases_ingredient ON ingredient_aliases (ingredient_id);
CREATE INDEX ix_ingredient_aliases_alias_trgm ON ingredient_aliases USING gin (alias gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- 브랜드·상품
-- ---------------------------------------------------------------------------

CREATE TABLE brands (
    id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name varchar(100) NOT NULL,
    CONSTRAINT uk_brands_name UNIQUE (name)
);

CREATE TABLE products (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id            bigint       NOT NULL REFERENCES brands (id),
    category_id         bigint       NOT NULL REFERENCES categories (id),
    data_source_id      bigint       NOT NULL REFERENCES data_sources (id),
    name                varchar(200) NOT NULL,
    description         text,
    image_url           varchar(500),
    volume              varchar(50),
    price               integer,
    price_updated_at    timestamptz,
    ingredient_status   varchar(20)  NOT NULL DEFAULT 'INCOMPLETE',
    status              varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    source_url          varchar(500),
    source_collected_at date         NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    -- 데이터 적재 시 같은 상품을 다시 넣으면 갱신되도록 하는 자연키
    CONSTRAINT uk_products_brand_name UNIQUE (brand_id, name),
    CONSTRAINT ck_products_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_products_ingredient_status CHECK (ingredient_status IN ('COMPLETE', 'INCOMPLETE')),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);
CREATE INDEX ix_products_category_status ON products (category_id, status);
CREATE INDEX ix_products_name_trgm ON products USING gin (name gin_trgm_ops);
-- brand_id 단독 조회는 uk_products_brand_name 인덱스의 앞 컬럼으로 처리된다

-- 전성분. 표시 순서가 곧 함량 순서라 순서를 PK에 포함한다.
CREATE TABLE product_ingredients (
    product_id    bigint       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    display_order integer      NOT NULL,
    ingredient_id bigint       REFERENCES ingredients (id), -- 매칭 실패 시 NULL (FR-ADM-03)
    raw_name      varchar(300) NOT NULL,                     -- 제품에 표기된 이름 그대로
    PRIMARY KEY (product_id, display_order),
    CONSTRAINT ck_product_ingredients_order CHECK (display_order >= 1)
);
CREATE INDEX ix_product_ingredients_ingredient ON product_ingredients (ingredient_id);

CREATE TABLE product_skin_types (
    product_id bigint      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    skin_type  varchar(20) NOT NULL,
    PRIMARY KEY (product_id, skin_type),
    CONSTRAINT ck_product_skin_types_type CHECK (skin_type IN ('DRY', 'OILY', 'COMBINATION', 'NORMAL'))
);

CREATE TABLE product_concern_tags (
    product_id bigint NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    concern_id bigint NOT NULL REFERENCES skin_concerns (id),
    PRIMARY KEY (product_id, concern_id)
);
CREATE INDEX ix_product_concern_tags_concern ON product_concern_tags (concern_id);
