-- PDN (Parti Démocrate National) and ND (Parti des Néo-Démocrates)
-- are separate political organisations. Keep ND and every existing ND
-- affiliation untouched; add PDN as its own catalogue identity.
--
-- Maroc.ma lists both parties independently, and PDN publishes a separate
-- 2026-2030 programme at https://www.pdn.ma/programme/.
UPDATE political_parties
   SET sort_order = sort_order + 1
 WHERE sort_order >= 17
   AND sort_order < 98;

INSERT INTO political_parties (
    code, name_fr, name_ar, color, sort_order, visible,
    symbol_label_fr, symbol_label_ar, symbol_asset, symbol_verified
) VALUES (
    'PDN',
    'Parti Démocrate National',
    'الحزب الديمقراطي الوطني',
    '#333333',
    17,
    TRUE,
    'Parapluie',
    'المظلة',
    '/assets/parties/pdn-display.png',
    TRUE
);
