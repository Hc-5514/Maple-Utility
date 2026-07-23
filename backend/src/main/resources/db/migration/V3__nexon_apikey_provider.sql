UPDATE users
SET oauth_provider = 'NEXON_APIKEY'
WHERE oauth_provider = 'NEXON';

ALTER TABLE users DROP CONSTRAINT chk_users_oauth_provider;

ALTER TABLE users
    ADD CONSTRAINT chk_users_oauth_provider CHECK (oauth_provider IN ('KAKAO', 'NEXON_APIKEY'));
