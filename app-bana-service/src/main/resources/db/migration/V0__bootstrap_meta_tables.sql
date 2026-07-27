-- V0__bootstrap_meta_tables.sql
-- Creates the three platform meta tables that later changesets depend on.
--
-- These were historically created lazily at runtime by JdbcManager.ensureMetaTable(),
-- which runs *after* Liquibase in ApiServer.startJdk(). Every long-lived dev database
-- happened to have them already, so the gap was invisible — but against a fresh database
-- (e.g. a CI service container) V10 fails with:
--     ERROR: relation "appbana_schemas" does not exist
--
-- Creating them here makes the migration chain self-contained and provisionable from
-- scratch. JdbcManager still issues the same CREATE TABLE IF NOT EXISTS statements, which
-- become no-ops. Definitions below must stay in sync with the "postgres" branch of
-- JdbcManager.ensureMetaTableFor.

CREATE TABLE IF NOT EXISTS appbana_schemas (
    name VARCHAR(200) PRIMARY KEY,
    json TEXT
);

CREATE TABLE IF NOT EXISTS appbana_migrations (
    id          BIGSERIAL PRIMARY KEY,
    schema_name VARCHAR(200),
    sql         TEXT,
    executed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS appbana_audit (
    id           BIGSERIAL PRIMARY KEY,
    ts           TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    op           VARCHAR(20),
    entity       VARCHAR(200),
    pk           VARCHAR(200),
    actor        VARCHAR(200),
    before_json  TEXT,
    after_json   TEXT,
    changes_json TEXT
);
