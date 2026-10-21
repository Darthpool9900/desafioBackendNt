package com.teste.pagamento.repositories;

import com.teste.pagamento.Models.Checkout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutRepository extends JpaRepository<Checkout,String> {

}