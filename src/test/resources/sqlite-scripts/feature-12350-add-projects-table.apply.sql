-- Add a new table for project tracking
CREATE TABLE IF NOT EXISTS projects (
    project_id TEXT PRIMARY KEY,
    project_name TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT,
    budget REAL,
    status TEXT DEFAULT 'active',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create an index on project status
CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);

-- Create an index on project start date
CREATE INDEX IF NOT EXISTS idx_projects_start_date ON projects(start_date);

-- Insert sample project data
INSERT INTO projects (project_id, project_name, start_date, end_date, budget, status) VALUES
    ('proj001', 'Website Redesign', '2023-01-15', '2023-06-30', 50000.00, 'completed'),
    ('proj002', 'Mobile App Development', '2023-03-01', '2023-12-31', 120000.00, 'active'),
    ('proj003', 'Database Migration', '2023-07-01', '2023-09-30', 75000.00, 'active');