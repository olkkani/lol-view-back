CREATE TABLE "refresh_tokens"
(
    "id"         bigint PRIMARY KEY,
    "user_id"    bigint NOT NULL,
    "token_hash" varchar NOT NULL,
    "expires_at" timestamp NOT NULL,
    "created_at" timestamp NOT NULL,
    "revoked_at" timestamp
);

ALTER TABLE "refresh_tokens"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "refresh_tokens"
    ADD CONSTRAINT "uq_refresh_tokens_token_hash"
    UNIQUE ("token_hash");

CREATE INDEX "idx_refresh_tokens_user_id" ON "refresh_tokens" ("user_id");
