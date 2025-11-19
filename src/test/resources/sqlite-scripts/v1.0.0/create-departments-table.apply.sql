CREATE TABLE departments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    department_id TEXT NOT NULL UNIQUE,
    department_name TEXT NOT NULL,
    manager_id INTEGER,
    location TEXT,
    budget REAL,
    is_active INTEGER DEFAULT 1,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_departments_department_id ON departments(department_id);
CREATE INDEX idx_departments_manager_id ON departments(manager_id);
CREATE INDEX idx_departments_location ON departments(location);