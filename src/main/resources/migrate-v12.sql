-- Run once against an existing Derby database while the application is stopped.
-- Discards previously saved card details and keeps accounts and orders.
ALTER TABLE app.users ADD COLUMN card_last_four VARCHAR(4)
    CHECK (card_last_four IS NULL OR LENGTH(card_last_four) = 4);
ALTER TABLE app.users DROP COLUMN card;
ALTER TABLE app.users DROP COLUMN cvv;
ALTER TABLE app.users DROP COLUMN expiration_month;
ALTER TABLE app.users DROP COLUMN expiration_year;
