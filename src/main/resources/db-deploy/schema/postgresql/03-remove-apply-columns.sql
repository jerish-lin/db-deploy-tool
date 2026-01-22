-- PostgreSQL Migration: Remove apply_script_content and apply_verify_script_content columns
-- This script removes the columns that are no longer used (apply scripts are read from files, not stored in DB)

-- Drop the columns from schemaflow_changelog_script table
ALTER TABLE schemaflow_changelog_script DROP COLUMN IF EXISTS apply_script_content;
ALTER TABLE schemaflow_changelog_script DROP COLUMN IF EXISTS apply_verify_script_content;