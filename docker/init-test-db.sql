-- Runs once, on first container init (postgres-data volume empty), alongside the default
-- POSTGRES_DB database — creates the separate database the backend's test profile points at
-- (backend/src/test/resources/application.yaml), so `gradlew.bat test` doesn't run against dev
-- data.
CREATE DATABASE budgetsandsubscriptions_test OWNER budgetsandsubscriptions;
