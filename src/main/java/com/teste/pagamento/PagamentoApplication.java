package com.teste.pagamento;

import com.teste.pagamento.Connections.RedisConnect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PagamentoApplication {

	public static void main(String[] args) {

		SpringApplication.run(PagamentoApplication.class, args);
	}

}
