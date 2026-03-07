CREATE TABLE issue_types (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(100) NOT NULL UNIQUE,  -- e.g. NULL_DEREF, RESOURCE_LEAK
    name        VARCHAR(255) NOT NULL,          -- e.g. "Null Dereference"
    description TEXT,
    category    VARCHAR(100),                   -- e.g. "Security", "Correctness", "Style"
    severity_hint VARCHAR(50),                  -- suggested default severity
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add FK from golden_dataset_entries to issue_types (optional, nullable)
ALTER TABLE golden_dataset_entries ADD COLUMN issue_type_id BIGINT REFERENCES issue_types(id);

-- Seed with common issue types
INSERT INTO issue_types (code, name, description, category, severity_hint) VALUES
('NULL_DEREF',       'Null Dereference',         'Potential null pointer dereference',              'Correctness', 'MAJOR'),
('RESOURCE_LEAK',    'Resource Leak',             'Resource not closed or released properly',        'Correctness', 'MAJOR'),
('SQL_INJECTION',    'SQL Injection',             'User input used directly in SQL query',           'Security',    'CRITICAL'),
('XSS',              'Cross-Site Scripting',      'Unescaped user input rendered in HTML',           'Security',    'CRITICAL'),
('RACE_CONDITION',   'Race Condition',            'Shared state accessed without synchronization',   'Concurrency', 'CRITICAL'),
('DEAD_CODE',        'Dead Code',                 'Code that can never be reached or executed',      'Style',       'MINOR'),
('MAGIC_NUMBER',     'Magic Number',              'Unexplained numeric literal should be a constant','Style',       'NITPICK'),
('ERROR_HANDLING',   'Missing Error Handling',    'Exception or error not handled appropriately',    'Correctness', 'MAJOR'),
('PERF_ISSUE',       'Performance Issue',         'Inefficient algorithm or unnecessary work',       'Performance', 'MINOR'),
('STYLE_VIOLATION',  'Style Violation',           'Code does not follow project conventions',        'Style',       'NITPICK'),
('LOGIC_ERROR',      'Logic Error',               'Incorrect logic or wrong condition',              'Correctness', 'MAJOR'),
('MISSING_VALIDATION','Missing Input Validation', 'User input not validated before use',             'Security',    'MAJOR');
