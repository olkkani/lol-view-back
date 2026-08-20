CREATE TABLE "user_identities"
(
    "id"                bigint PRIMARY KEY,
    "user_id"           bigint,
    "provider"          varchar NOT NULL,
    "provider_user_id"  varchar NOT NULL
);

ALTER TABLE "user_identities"
    ADD FOREIGN KEY ("user_id") REFERENCES "users" ("id") DEFERRABLE;

ALTER TABLE "user_identities"
    ADD CONSTRAINT "uq_user_identities_provider_provider_user_id"
    UNIQUE ("provider", "provider_user_id");
