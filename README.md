# Checkout BFF — Desafio Técnico CKW

Backend for Frontend (BFF) para um fluxo de checkout de assinaturas, desenvolvido com **Java 27**, **Spring Boot 4.1.1** e **Gradle**. A aplicação prepara o carrinho no pré-checkout, valida o meio de pagamento, simula a cobrança e fornece respostas específicas para as telas de pagamento, sucesso e erro.

O projeto foi desenvolvido com foco em separação de responsabilidades, consistência transacional, segurança dos dados de pagamento, prevenção de cobranças duplicadas, integração assíncrona e testes automatizados.

> **Escopo:** os pagamentos são simulados. Não há integração com gateway financeiro real nem cobrança de valores reais.

## Tecnologias

| Tecnologia | Utilização |
|---|---|
| Java 27 / Spring Boot 4.1.1 | API REST, validações e regras de negócio |
| Gradle | Build e execução dos testes |
| PostgreSQL 17 | Persistência de checkouts, eventos e controle de processamento |
| Flyway | Versionamento e execução das migrações do banco |
| Redis / Lettuce | Armazenamento temporário do carrinho, TTL e controle de concorrência |
| RabbitMQ | Mensageria assíncrona |
| Transactional Outbox | Registro de eventos na mesma transação dos dados de checkout |
| Jackson 3 | Serialização e desserialização JSON |
| JUnit / Mockito | Testes unitários |
| Testcontainers | Testes de integração com serviços reais em contêineres |
| Docker / Docker Compose | Empacotamento e execução da infraestrutura |

## Visão geral da arquitetura

```text
Cliente / Insomnia
       |
       v
Spring Boot — BFF
       |
       +-- POST /operations/pre-checkout
       |       |
       |       +-- Serviço de produtos (mock / interface própria)
       |       +-- Serviço de meios de pagamento (mock / interface própria)
       |       +-- Redis: carrinho temporário (TTL de 30 minutos)
       |
       +-- POST /operations/checkout
               |
               +-- Validação do carrinho e do pagamento
               +-- Controle de concorrência / prevenção de duplicidade
               +-- PostgreSQL: checkout + evento de outbox
                         |
                         v
                    Publicador agendado
                         |
                         v
                       RabbitMQ
                         |
                         v
                    Consumidor idempotente
                         |
                         v
                   PostgreSQL: processed_events
```

### Decisões técnicas

**BFF e serviços externos simulados.** O BFF não define o catálogo e os meios de pagamento diretamente no controller: essas informações são obtidas por serviços específicos, simulando dependências externas.

**Redis.** O pré-checkout armazena um carrinho temporário identificado por UUID, com TTL de **1.800 segundos (30 minutos)**. O checkout utiliza controle de concorrência para evitar processamento simultâneo do mesmo carrinho.

**PostgreSQL e Flyway.** O banco mantém os dados persistentes e as migrações permitem reproduzir a estrutura de forma consistente em outros ambientes.

**Transactional Outbox.** A operação de checkout e o registro do evento correspondente são realizados na mesma transação de banco. Se a gravação do evento falhar, a transação é revertida, evitando que o checkout fique persistido sem seu evento associado.

**RabbitMQ e idempotência.** Um publicador agendado encaminha eventos da outbox ao RabbitMQ. O consumidor utiliza o controle de eventos processados para não aplicar duas vezes o mesmo evento, mesmo que a mensagem seja entregue novamente.

**Dados sensíveis.** O número completo do cartão e o CVV não devem ser persistidos. A resposta de sucesso utiliza somente os dados necessários à interface, como os últimos quatro dígitos.

## Pré-requisitos

### Execução via Docker (recomendada)

- Docker Desktop ou Docker Engine com Docker Compose.
- Portas `8080` (API) e `15672` (painel RabbitMQ) disponíveis, conforme o `compose.yaml`.

### Execução local

- JDK 27.
- Docker disponível para subir PostgreSQL, Redis e RabbitMQ, ou instâncias equivalentes já configuradas.
- O projeto inclui o Gradle Wrapper; não é necessário instalar Gradle globalmente.

## Como executar com Docker Compose

Na raiz do backend, onde ficam `Dockerfile`, `compose.yaml` e `build.gradle`, crie um arquivo `.env`:

```dotenv
POSTGRES_PASSWORD=defina_uma_senha_local
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=defina_outra_senha_local
```

> Nunca publique senhas reais. Adicione `.env` ao `.gitignore` e mantenha apenas um `.env.example` com valores ilustrativos no repositório.

Construa a imagem e inicialize os serviços:

```bash
docker compose build
docker compose up -d
```

Verifique o estado dos contêineres:

```bash
docker compose ps
```

Acompanhe os logs do backend:

```bash
docker compose logs -f backend
```

Endereços esperados com o Compose apresentado:

| Serviço | Endereço |
|---|---|
| API | `http://localhost:8080` |
| RabbitMQ Management | `http://localhost:15672` |

Acesse o painel RabbitMQ com as credenciais `RABBITMQ_USER` e `RABBITMQ_PASSWORD` definidas no `.env`.

Para encerrar os serviços:

```bash
docker compose down
```

Para remover **também os volumes e dados persistidos**:

```bash
docker compose down -v
```

> **Atenção:** `down -v` apaga os volumes gerenciados pelo Compose, incluindo os dados locais do PostgreSQL.

### Configuração por ambiente

O `compose.yaml` fornece ao backend as variáveis de conexão com os demais contêineres:

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pagamento
  SPRING_DATASOURCE_USERNAME: postgres
  SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
  SPRING_RABBITMQ_HOST: rabbitmq
  SPRING_RABBITMQ_PORT: 5672
  SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER}
  SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
  APP_REDIS_HOST: redis
  APP_REDIS_PORT: 6379
```

Dentro da rede Docker, as dependências são acessadas pelos nomes dos serviços (`postgres`, `redis`, `rabbitmq`), e não por `localhost`. As propriedades do Spring Boot podem ser sobrescritas pelas variáveis de ambiente, permitindo preservar as configurações de desenvolvimento local.

> Os nomes de variáveis e do banco acima correspondem ao `compose.yaml` preparado para esta entrega. Se os arquivos finais tiverem sido alterados, mantenha esta seção sincronizada com eles.

## Contrato da API

Base URL: `http://localhost:8080`

Todas as requisições de exemplo utilizam `Content-Type: application/json`.

### 1. Pré-checkout

`POST /operations/pre-checkout`

O endpoint recebe os produtos selecionados, consulta os serviços de catálogo e meios de pagamento, cria o carrinho e retorna as informações necessárias à tela de pagamento.

**Requisição:**

```json
{
  "products": [
    { "slug": "premium" }
  ]
}
```

**Exemplo de resposta de sucesso:**

```json
{
  "cartId": "a9e48fd2-3d2c-4b6b-8a29-50c29b602b1d",
  "products": [
    {
      "slug": "premium",
      "name": "Plano Premium",
      "description": "Assinatura mensal com catálogo completo",
      "price": 39.90,
      "currency": "BRL"
    }
  ],
  "paymentMethods": [
    { "name": "Cartão de Crédito" },
    { "name": "Cartão de Débito" },
    { "name": "Pix" }
  ]
}
```

O `cartId` é gerado a cada pré-checkout; o UUID acima é apenas ilustrativo.

**Catálogo sugerido pelo enunciado:**

| Plano | Preço mensal |
|---|---:|
| Básico | R$ 19,90 |
| Premium | R$ 39,90 |
| Família | R$ 59,90 |

O contrato recebe uma lista de objetos com `slug`; **não existe um campo `quantity` exigido pelo desafio**. Os slugs exatos aceitos devem corresponder aos cadastrados no serviço de produtos da aplicação.

### 2. Checkout com Pix — sucesso

`POST /operations/checkout`

Utilize o `cartId` retornado no pré-checkout:

```json
{
  "cartId": "SUBSTITUA_PELO_CART_ID",
  "paymentMethod": {
    "name": "Pix"
  }
}
```

Para Pix, **não** envie o objeto `card`. Em caso de aprovação, a API retorna o status `APROVADO`, a data do pagamento, o valor, a moeda, os produtos e um código Pix simulado, conforme o contrato do desafio.

### 3. Checkout com cartão — sucesso

`POST /operations/checkout`

```json
{
  "cartId": "SUBSTITUA_PELO_CART_ID",
  "paymentMethod": {
    "name": "Cartão de Crédito",
    "card": {
      "holderName": "Ana Souza",
      "number": "4111111111114242",
      "expiry": "12/28",
      "cvv": "123"
    }
  }
}
```

**Exemplo de resposta de sucesso, conforme o contrato do desafio:**

```json
{
  "cartId": "a9e48fd2-3d2c-4b6b-8a29-50c29b602b1d",
  "status": "APROVADO",
  "paidAt": "2026-10-14T16:00:00Z",
  "payment": {
    "method": "Cartão de Crédito",
    "lastDigits": "4242",
    "amount": 39.90,
    "currency": "BRL"
  },
  "products": [
    {
      "slug": "premium",
      "name": "Plano Premium",
      "price": 39.90
    }
  ]
}
```

Os valores de `cartId` e `paidAt` são ilustrativos. Não reutilize o mesmo carrinho após um pagamento aprovado.

### 4. Checkout com cartão — recusa simulada

O enunciado determina que **cartões terminados em `0002` sejam recusados**. Crie um novo carrinho no pré-checkout e envie:

```json
{
  "cartId": "SUBSTITUA_PELO_CART_ID",
  "paymentMethod": {
    "name": "Cartão de Crédito",
    "card": {
      "holderName": "Ana Souza",
      "number": "4111111111110002",
      "expiry": "12/28",
      "cvv": "123"
    }
  }
}
```

**Formato esperado de erro:**

```json
{
  "displayType": "BLOCK",
  "errorCode": "PAYMENT_DECLINED",
  "cartId": "a9e48fd2-3d2c-4b6b-8a29-50c29b602b1d",
  "frontendError": {
    "code": "P-1001",
    "title": "Pagamento não aprovado",
    "description": "Não foi possível concluir o pagamento. Tente novamente ou escolha outro meio.",
    "primaryButton": {
      "label": "Tentar novamente",
      "url": "RETRY"
    },
    "secondaryButton": {
      "label": "Trocar meio de pagamento",
      "url": "/checkout/pagamento"
    }
  }
}
```

Erros destinados à interface utilizam HTTP `4xx` e um objeto padronizado com `displayType`, `errorCode`, `cartId` e `frontendError`. O `frontendError` inclui `code`, `title`, `description` e pelo menos `primaryButton`. O tipo `BLOCK` representa erro de tela; `INTERVENTION` representa um aviso que não encerra o fluxo.

### 5. Produto inexistente

`POST /operations/pre-checkout`

```json
{
  "products": [
    { "slug": "produto-inexistente" }
  ]
}
```

**Exemplo de erro observado na aplicação:**

```json
{
  "displayType": "BLOCK",
  "errorCode": "PRODUCT_NOT_FOUND",
  "cartId": null,
  "frontendError": {
    "code": "P-1002",
    "title": "Produto não encontrado",
    "description": "O produto solicitado não existe ou não está disponível.",
    "primaryButton": {
      "label": "Voltar ao pagamento",
      "url": "/checkout/pagamento"
    },
    "secondaryButton": null
  }
}
```

## Regras de negócio

1. O checkout exige um `cartId` válido, previamente criado no pré-checkout.
2. O meio de pagamento escolhido deve fazer parte dos métodos retornados para o carrinho.
3. Um produto inexistente gera erro no contrato padronizado para o frontend.
4. Um cartão cujo número termina em `0002` deve ser recusado.
5. Um carrinho já aprovado não pode ser pago novamente.
6. O número completo do cartão e o CVV não devem ser persistidos.
7. As respostas de sucesso expõem apenas os dados necessários à interface.
8. Os carrinhos temporários possuem expiração configurada no Redis.
9. O processamento do checkout protege contra concorrência e operações duplicadas.
10. A gravação do checkout e do evento da outbox é transacional.
11. O consumidor de eventos evita processamento repetido de uma mesma mensagem.

## Testes automatizados

Execute a suíte completa na raiz do projeto:

**Windows (PowerShell):**

```powershell
.\gradlew.bat clean test
```

**Linux / macOS:**

```bash
./gradlew clean test
```

Para executar somente os testes de integração:

```powershell
.\gradlew.bat clean test --tests "*CheckoutIntegrationTest"
```

Os testes de integração utilizam **Testcontainers**, portanto é necessário ter o Docker em execução para disponibilizar as dependências dos testes.

### Cobertura realizada

A suíte desenvolvida contempla **39 testes unitários** e **8 testes de integração**, totalizando **47 testes**, considerando a execução completa das duas classes de teste/suítes já verificadas durante o desenvolvimento.

Os oito cenários de integração incluem:

| Cenário | Verificação |
|---|---|
| Checkout aprovado e outbox | Persistência do checkout e do evento na mesma transação |
| Checkout duplicado | Um carrinho aprovado não pode ser pago novamente |
| Redis / TTL / lock | Expiração do carrinho e bloqueio de processamento concorrente |
| Produto inválido | Resposta de erro e ausência de checkout persistido |
| Cartão recusado | Falha de pagamento sem persistência indevida |
| Mensagem duplicada | Consumidor idempotente |
| Publicação da outbox | Entrega do evento pelo publicador ao consumidor |
| Falha na gravação da outbox | Rollback da transação de checkout |

> Os testes unitários e os oito testes de integração foram executados separadamente com sucesso durante o desenvolvimento. Confira o resultado da execução final de `clean test` antes da publicação do repositório.

O relatório HTML de testes gerado pelo Gradle fica em:

```text
build/reports/tests/test/index.html
```

A pasta `build/` contém artefatos gerados e normalmente não deve ser versionada.

## Fluxos para avaliação rápida

### Fluxo aprovado com Pix

1. Envie `POST /operations/pre-checkout` com o slug `premium`.
2. Copie o `cartId` da resposta.
3. Envie `POST /operations/checkout` com o método `Pix`.
4. Verifique o retorno `APROVADO` e os dados do pagamento.

### Fluxo aprovado com cartão

1. Crie um **novo** carrinho.
2. Faça o checkout com `Cartão de Crédito` e número de teste terminado em `4242`.
3. Verifique o status de aprovação e `lastDigits`.

### Fluxo recusado

1. Crie um **novo** carrinho.
2. Faça o checkout com cartão terminado em `0002`.
3. Verifique o erro `PAYMENT_DECLINED` no formato `frontendError`.

### Fluxo de produto inexistente

1. Envie o pré-checkout com `produto-inexistente`.
2. Verifique `PRODUCT_NOT_FOUND` e o objeto `frontendError`.

### Fluxo de pagamento duplicado

1. Crie um carrinho e conclua um pagamento aprovado.
2. Tente realizar o checkout novamente com o mesmo `cartId`.
3. Verifique que a aplicação não aprova um segundo pagamento para esse carrinho.

## Segurança e boas práticas

- Não utilizar dados reais de cartão nos testes.
- Não persistir PAN (número completo do cartão) nem CVV.
- Não publicar arquivos `.env`, senhas, tokens ou segredos no repositório.
- Manter configuração por variáveis de ambiente, separando desenvolvimento e Docker.
- Tratar erros de negócio por meio de um contrato consistente e previsível.
- Evitar efeitos duplicados em consumidores de mensagens.
- Preservar a consistência entre alterações no banco e eventos por meio da outbox transacional.

## Observações sobre o desafio

A solução atende ao fluxo de BFF proposto: **pré-checkout**, **checkout**, **simulação de sucesso/recusa** e **erros estruturados para o frontend**. Os serviços de produtos e meios de pagamento são simulados e acessados por abstrações próprias, conforme permitido pelo enunciado.

Também explora os diferenciais técnicos sugeridos: **Spring Boot**, **PostgreSQL**, **RabbitMQ**, **Redis**, **testes automatizados** e **Docker**. A estratégia de Redis para carrinhos e locks, além do uso de transactional outbox e idempotência, amplia a robustez do fluxo.

**Autor:** Rafael Zachesky de Oliveira  
**Finalidade:** Desafio técnico backend — CKW.
