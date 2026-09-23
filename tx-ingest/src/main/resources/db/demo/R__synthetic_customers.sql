-- Synthetic customers for the demo and the traffic simulator. Every name is invented.
--
-- A repeatable migration in a location of its own, so it can be switched off
-- (spring.flyway.locations) without leaving a gap in the versioned history.

INSERT INTO sentinel.customer (id, full_name, segment, country, risk_rating, onboarded_on)
VALUES ('C-10001', 'Anna Weber',              'RETAIL',    'DE', 'LOW',    DATE '2016-03-14'),
       ('C-10002', 'Jonas Becker',            'RETAIL',    'DE', 'LOW',    DATE '2019-11-02'),
       ('C-10003', 'Lea Schmitt',             'RETAIL',    'AT', 'MEDIUM', DATE '2021-06-21'),
       ('C-10004', 'Mehmet Yilmaz',           'RETAIL',    'DE', 'LOW',    DATE '2018-01-09'),
       ('C-10005', 'Sofia Rossi',             'RETAIL',    'IT', 'MEDIUM', DATE '2023-02-27'),
       ('C-10006', 'Pieter de Vries',         'RETAIL',    'NL', 'LOW',    DATE '2015-08-30'),
       ('C-20001', 'Kessler Bau GmbH',        'BUSINESS',  'DE', 'MEDIUM', DATE '2012-05-16'),
       ('C-20002', 'Nordlicht Logistik GmbH', 'BUSINESS',  'DE', 'LOW',    DATE '2017-10-04'),
       ('C-20003', 'Cafe Morgenrot',          'BUSINESS',  'DE', 'HIGH',   DATE '2024-04-11'),
       ('C-30001', 'Havelland Maschinen AG',  'CORPORATE', 'DE', 'LOW',    DATE '2009-02-19'),
       ('C-30002', 'Altmark Energie SE',      'CORPORATE', 'DE', 'MEDIUM', DATE '2011-09-07')
ON CONFLICT (id) DO NOTHING;
