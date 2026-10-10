-- Synthetic review display data for the explicitly selected dev profile only.
-- Demo accounts are disabled and use no usable password hash.
INSERT INTO users (email, password_hash, role, enabled)
VALUES
    ('demo-owner@primeskill.invalid', '!disabled-demo-account!', 'USER', FALSE),
    ('demo-reviewer-one@primeskill.invalid', '!disabled-demo-account!', 'USER', FALSE),
    ('demo-reviewer-two@primeskill.invalid', '!disabled-demo-account!', 'USER', FALSE),
    ('demo-reviewer-three@primeskill.invalid', '!disabled-demo-account!', 'USER', FALSE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_profiles (user_id, display_name)
SELECT id,
       CASE email
           WHEN 'demo-owner@primeskill.invalid' THEN 'Demo Tool Owner'
           WHEN 'demo-reviewer-one@primeskill.invalid' THEN 'Demo Reviewer One'
           WHEN 'demo-reviewer-two@primeskill.invalid' THEN 'Demo Reviewer Two'
           ELSE 'Demo Reviewer Three'
       END
FROM users
WHERE email IN (
    'demo-owner@primeskill.invalid',
    'demo-reviewer-one@primeskill.invalid',
    'demo-reviewer-two@primeskill.invalid',
    'demo-reviewer-three@primeskill.invalid'
)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO categories (name, slug, description)
VALUES ('Demo Tools', 'demo-tools', 'Synthetic data for local development only')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO tools (name, slug, short_description, description, category_id, owner_id, status)
SELECT 'Demo Review Tool', 'demo-review-tool', 'Synthetic local example',
       'A local development example used to preview review summaries and review cards.',
       categories.id, users.id, 'PUBLISHED'
FROM categories
CROSS JOIN users
WHERE categories.slug = 'demo-tools'
  AND users.email = 'demo-owner@primeskill.invalid'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO reviews (user_id, tool_id, rating, comment)
SELECT users.id, tools.id, demo.rating, demo.comment
FROM (VALUES
    ('demo-reviewer-one@primeskill.invalid', 5::SMALLINT, 'Synthetic development review.'),
    ('demo-reviewer-two@primeskill.invalid', 4::SMALLINT, 'Synthetic development review.'),
    ('demo-reviewer-three@primeskill.invalid', 3::SMALLINT, 'Synthetic development review.')
) AS demo(email, rating, comment)
JOIN users ON users.email = demo.email
JOIN tools ON tools.slug = 'demo-review-tool'
ON CONFLICT (user_id, tool_id) DO NOTHING;
