ALTER TABLE vehicle ADD COLUMN dealer_registration BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE vehicle SET dealer_registration = TRUE WHERE vin = 'WAUZZZ8V5KA000002';
