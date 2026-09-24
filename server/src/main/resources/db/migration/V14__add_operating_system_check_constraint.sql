ALTER TABLE configuration
    ADD CONSTRAINT chk_operating_system
        CHECK (operating_system IN ('Windows 10', 'Windows 11', 'Linux', 'macOS'));
