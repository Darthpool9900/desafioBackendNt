CREATE TABLE checkouts (
   cart_id VARCHAR(255) PRIMARY KEY,
   status VARCHAR(50) NOT NULL,
   paid_at TIMESTAMP(6) WITH TIME ZONE,
   method VARCHAR(255),
   last_digits VARCHAR(4),
   amount NUMERIC(12,2),
   currency VARCHAR(3)
);

CREATE TABLE checkout_products (
   cart_id VARCHAR(255) NOT NULL,
   slug VARCHAR(255),
   name VARCHAR(255),
   price NUMERIC(12,2),

   CONSTRAINT fk_checkout_products
       FOREIGN KEY (cart_id)
           REFERENCES checkouts(cart_id)
);