INSERT INTO auth.products (code, name) VALUES ('DEFAULT', 'Default Product')
    ON CONFLICT (code) DO NOTHING;
