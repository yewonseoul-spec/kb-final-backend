INSERT INTO benefit_category (
    category_code,
    category_name,
    lclsf_nm,
    display_order
)
VALUES
    ('1', '일자리', '일자리', 1),
    ('2', '주거', '주거', 2),
    ('3', '교육', '교육', 3),
    ('4', '복지문화', '복지문화', 4),
    ('5', '참여권리', '참여권리', 5)
ON DUPLICATE KEY UPDATE
                     category_name = VALUES(category_name),
                     lclsf_nm = VALUES(lclsf_nm),
                     display_order = VALUES(display_order);