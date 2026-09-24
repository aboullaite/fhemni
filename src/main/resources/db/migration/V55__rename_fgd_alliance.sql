-- FGD and PSU retain their distinct internal identities, while their shared
-- public catalogue and 2026 programme are presented under the official
-- alliance name used by the Ministry of Interior.
UPDATE political_parties
   SET name_ar = 'تحالف اليسار',
       name_fr = 'Alliance de la Gauche'
 WHERE code = 'FGD';
