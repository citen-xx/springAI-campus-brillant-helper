-- Apply once before using the idempotent knowledge import pipeline.
ALTER TABLE knowledge_doc
    ADD COLUMN content_hash CHAR(64) NULL COMMENT 'SHA-256 of the source file',
    ADD COLUMN document_type VARCHAR(32) NULL COMMENT 'lowercase source extension',
    ADD UNIQUE KEY uk_knowledge_doc_content_hash (content_hash);
