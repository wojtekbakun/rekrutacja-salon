INSERT INTO vehicle_document (vehicle_id, type, number, issue_date, expiry_date, status) VALUES
    ((SELECT id FROM vehicle WHERE vin = 'WVWZZZ1KZAW000001'), 'COC', 'COC/2025/000001', DATE '2025-11-20', NULL, 'VERIFIED'),
    ((SELECT id FROM vehicle WHERE vin = 'WVWZZZ1KZAW000001'), 'OC_POLICY', 'OC/2026/123456', DATE '2026-11-01', DATE '2027-10-31', 'PENDING'),
    ((SELECT id FROM vehicle WHERE vin = 'WAUZZZ8V5KA000002'), 'SALES_AGREEMENT', 'UM/2026/0002', DATE '2026-10-05', NULL, 'PENDING');
